package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.parser.util.MAX_DECOMPRESSED
import com.example.splitreader.domain.parser.util.effectiveMaxDecompressed
import com.example.splitreader.domain.parser.util.readZipEntries
import com.example.splitreader.domain.parser.util.stableId
import com.example.splitreader.domain.parser.util.sweepStaleSpillDirs
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import javax.inject.Inject

class EpubParser @Inject constructor() : BookParser {

    override val supportedExtensions = listOf("epub")
    override val priority = 10

    companion object {
        private const val SPILL_PREFIX = "epub-"
        private const val STALE_SPILL_AGE_MILLIS = 60 * 60 * 1000L // 1 hour
    }

    /** The process heap limit the memory budget is derived from; a seam so tests can simulate a small heap. */
    internal var heapLimit: () -> Long = { Runtime.getRuntime().maxMemory() }

    /** A seam so tests can control "now" for the stale-spill-dir sweep without real file-system timing. */
    internal var nowMillis: () -> Long = System::currentTimeMillis

    override fun canParse(fileName: String, mimeType: String, header: ByteArray): Boolean =
        fileName.endsWith(".epub", ignoreCase = true) ||
            mimeType.contains("epub", ignoreCase = true) ||
            // EPUB's leading "mimetype" entry carries "application/epub+zip" near the start.
            String(header, Charsets.ISO_8859_1).contains("epub", ignoreCase = true)

    private data class OpfData(
        val title: String,
        val author: String,
        val spineIds: List<String>,
        val manifestMap: Map<String, String>,
        val coverHref: String?,
        val navItemIds: Set<String>,
        val description: String?,
    )

    override suspend fun parse(uri: Uri, context: Context): Book {
        // Pictures are streamed out of the zip into a scratch folder (see readZipEntries) and copied
        // to their final place from there, so only the book's text is ever held in memory.
        val spillDir = File(context.cacheDir, "$SPILL_PREFIX${java.util.UUID.randomUUID()}")
        // A parse that died mid-import (process death) never reaches the `finally` below and leaves
        // its folder behind forever; sweep ones old enough that no parse could still be using them.
        // This parse's own folder is excluded so a sweep never races a concurrent parse.
        sweepStaleSpillDirs(context.cacheDir, SPILL_PREFIX, STALE_SPILL_AGE_MILLIS, keep = spillDir, now = nowMillis())
        try {
            return parse(uri, context, spillDir)
        } finally {
            spillDir.deleteRecursively()
        }
    }

    private suspend fun parse(uri: Uri, context: Context, spillDir: File): Book {
        val textExtensions = setOf("xml", "html", "xhtml", "opf", "ncx", "htm")
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")
        fun ext(name: String) = name.substringAfterLast('.').lowercase()

        // Text in memory, bounded by a heap fraction; pictures on disk; the whole book by the
        // zip-bomb bound MAX_DECOMPRESSED.
        val contents = context.contentResolver.openInputStream(uri)?.use { input ->
            readZipEntries(
                input,
                memoryBudget = effectiveMaxDecompressed(heapLimit()),
                totalBudget = MAX_DECOMPRESSED,
                spillDir = spillDir,
                spill = { ext(it) in imageExtensions },
            ) { name -> ext(name) in textExtensions || ext(name) in imageExtensions }
        } ?: throw IllegalStateException("Cannot open file")
        val entryMap: Map<String, ByteArray> = contents.inMemory
        val imageMap: Map<String, File> = contents.onDisk

        val containerXml = entryMap["META-INF/container.xml"]
            ?: entryMap.entries.firstOrNull { it.key.equals("META-INF/container.xml", ignoreCase = true) }?.value
            ?: throw IllegalStateException("Invalid EPUB: missing META-INF/container.xml")
        val rootfilePath = parseContainerXml(containerXml)

        val opfContent = entryMap[rootfilePath]
            ?: entryMap.entries.firstOrNull { it.key.equals(rootfilePath, ignoreCase = true) }?.value
            ?: throw IllegalStateException("Invalid EPUB: missing $rootfilePath")
        val opf = parseOpf(opfContent)

        val opfDir = rootfilePath.substringBeforeLast('/', "").let {
            if (it.isEmpty()) "" else "$it/"
        }

        val bookHash = stableId(uri.toString())
        val chapters = mutableListOf<Chapter>()
        // Images from image-only files (plates, cover/title pages). They never become chapters:
        // that would renumber the chapters of every book with an image cover page, and saved
        // positions, bookmarks and excerpts address chapters by index. They open the next chapter.
        val pendingImages = mutableListOf<String>()
        fun leading(): List<ChapterImage> =
            pendingImages.map { ChapterImage(0, it, inLegacyLayout = false) }.also { pendingImages.clear() }
        // The library already shows the cover; an image-only cover page must not repeat it in chapter 1.
        val coverKey = opf.coverHref?.let { normalizePath(opfDir + decodeSrc(it)) }

        opf.spineIds.filter { id -> id !in opf.navItemIds }.forEach { id ->
            val href = opf.manifestMap[id] ?: return@forEach
            val fullPath = "$opfDir$href"
            val htmlContent = entryMap[fullPath] ?: entryMap[href] ?: return@forEach
            val result = HtmlChapterExtractor.parse(htmlContent)
            // The XHTML file's directory is the base for relative image src URLs.
            val contentDir = fullPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            fun key(ref: HtmlChapterExtractor.ImageRef) = normalizePath(contentDir + decodeSrc(ref.src))
            fun save(ref: HtmlChapterExtractor.ImageRef): String? {
                val file = resolveImage(ref.src, contentDir, imageMap) ?: return null
                // The reader can't draw SVG; a newly collected one (title pages) would be an empty
                // slot. Images the old parser produced stay as they were (legacy layout).
                if (!ref.inLegacyLayout && (key(ref).endsWith(".svg", true) || ImageStore.header(file).firstOrNull() == '<'.code.toByte())) return null
                return ImageStore.save(context, file, "${bookHash}_${key(ref).substringAfterLast('/')}")
            }

            if (result.isImageOnly) {
                result.imageRefs.filter { key(it) != coverKey }.mapNotNullTo(pendingImages) { save(it) }
                return@forEach
            }

            // Content before the chapter heading -> standalone chapter (no title)
            if (result.preHeadingParagraphs.isNotEmpty()) {
                chapters.add(Chapter(
                    index = chapters.size,
                    title = "",
                    paragraphs = result.preHeadingParagraphs,
                    images = leading(),
                ))
            }

            // Main chapter: epigraph paragraphs first, then body paragraphs
            val allParagraphs = result.epigraphParagraphs + result.mainParagraphs
            if (result.headingTitle != null || allParagraphs.isNotEmpty()) {
                val images = result.imageRefs.mapNotNull { ref ->
                    save(ref)?.let { ChapterImage(ref.anchorParagraph, it, ref.inLegacyLayout) }
                }
                chapters.add(Chapter(
                    index = chapters.size,
                    title = result.headingTitle ?: "Chapter ${chapters.size + 1}",
                    paragraphs = allParagraphs,
                    epigraphCount = result.epigraphParagraphs.size,
                    images = leading() + images,
                ))
            }
        }
        // Image-only files after the last text: they close the book. A book of only images (a
        // picture book) gets one untitled chapter — it had none before, so nothing is renumbered.
        if (pendingImages.isNotEmpty()) {
            if (chapters.isNotEmpty()) {
                val last = chapters.last()
                chapters[chapters.lastIndex] = last.copy(
                    images = last.images + pendingImages.map { ChapterImage(last.paragraphs.size, it, inLegacyLayout = false) },
                )
            } else {
                chapters.add(Chapter(index = 0, title = "", paragraphs = emptyList(), images = leading()))
            }
        }

        val coverPath = opf.coverHref?.let { href ->
            val fullCoverPath = if (opfDir.isEmpty()) href else "$opfDir$href"
            val coverFile = resolveImage(href, opfDir, imageMap)
                ?: imageMap[fullCoverPath]
            coverFile?.let { saveCover(context, it, uri.toString(), fullCoverPath) }
        }

        val synopsis = SynopsisExtractor.build(opf.description, chapters.flatMap { it.paragraphs })
        return Book(opf.title, opf.author, chapters, uri.toString(), coverPath, synopsis)
    }

    private fun parseContainerXml(content: ByteArray): String {
        val doc = Jsoup.parse(content.inputStream(), "UTF-8", "", Parser.xmlParser())
        return doc.selectFirst("rootfile")?.attr("full-path")
            ?: throw IllegalStateException("rootfile not found in container.xml")
    }

    private fun parseOpf(content: ByteArray): OpfData {
        val doc = Jsoup.parse(content.inputStream(), "UTF-8", "", Parser.xmlParser())
        val title = doc.selectFirst("dc|title, dc\\:title")?.text()
            ?: doc.selectFirst("title")?.text()
            ?: "Unknown"
        val author = doc.selectFirst("dc|creator, dc\\:creator")?.text()
            ?: doc.selectFirst("creator")?.text()
            ?: "Unknown"
        val description = doc.selectFirst("dc|description, dc\\:description")?.text()
            ?: doc.selectFirst("description")?.text()
        val manifestMap = doc.select("item").associate { it.attr("id") to it.attr("href") }
        val navItemIds = doc.select("item").filter { "nav" in it.attr("properties") }.map { it.attr("id") }.toSet()
        val spineIds = doc.select("itemref")
            .filter { it.attr("linear").lowercase() != "no" }
            .map { it.attr("idref") }

        // Cover detection: EPUB 3 properties, then EPUB 2 meta, then id fallback
        val coverHref = doc.selectFirst("item[properties~=cover-image]")?.attr("href")
            ?: run {
                val metaCoverId = doc.selectFirst("meta[name=cover]")?.attr("content")
                if (metaCoverId != null) {
                    doc.selectFirst("item[id=$metaCoverId]")?.attr("href")
                } else null
            }
            ?: doc.selectFirst("item[id=cover], item[id=cover-image]")?.attr("href")

        return OpfData(title, author, spineIds, manifestMap, coverHref, navItemIds, description)
    }

    /** Strips fragment/query and URL-decodes an image src. */
    private fun decodeSrc(src: String): String {
        val clean = src.substringBefore('#').substringBefore('?')
        return try {
            java.net.URLDecoder.decode(clean, "UTF-8")
        } catch (_: Exception) {
            clean
        }
    }

    /** Resolves a relative path against [base], collapsing "./" and "../" segments. */
    private fun normalizePath(path: String): String {
        val out = ArrayDeque<String>()
        for (seg in path.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(seg)
            }
        }
        return out.joinToString("/")
    }

    /** Resolves an image src to its streamed-out file in [imageMap], trying exact, case-insensitive, then basename match. */
    private fun resolveImage(
        src: String,
        contentDir: String,
        imageMap: Map<String, File>,
    ): File? {
        val key = normalizePath(contentDir + decodeSrc(src))
        imageMap[key]?.let { return it }
        imageMap.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.let { return it.value }
        val basename = key.substringAfterLast('/')
        return imageMap.entries.firstOrNull { it.key.endsWith("/$basename", ignoreCase = true) }?.value
    }

    /** Copies the cover [source] to filesDir/covers/<stableId>.<ext> and returns its absolute path. */
    private fun saveCover(context: Context, source: File, uriKey: String, coverEntryPath: String): String? =
        try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val ext = coverEntryPath.substringAfterLast('.', "jpg").lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
            val coverFile = File(coversDir, "${stableId(uriKey)}.$ext")
            source.copyTo(coverFile, overwrite = true)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
}
