package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.parser.util.MAX_DECOMPRESSED
import com.example.splitreader.domain.parser.util.readZipEntries
import com.example.splitreader.domain.parser.util.stableId
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import javax.inject.Inject

class EpubParser @Inject constructor() : BookParser {

    override val supportedExtensions = listOf("epub")
    override val priority = 10

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
        val entryMap = mutableMapOf<String, ByteArray>()
        val imageMap = mutableMapOf<String, ByteArray>()
        val textExtensions = setOf("xml", "html", "xhtml", "opf", "ncx", "htm")
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")

        val allEntries = context.contentResolver.openInputStream(uri)?.use { input ->
            readZipEntries(input, MAX_DECOMPRESSED) { name ->
                val ext = name.substringAfterLast('.').lowercase()
                ext in textExtensions || ext in imageExtensions
            }
        } ?: throw IllegalStateException("Cannot open file")
        allEntries.forEach { (name, bytes) ->
            when (name.substringAfterLast('.').lowercase()) {
                in textExtensions -> entryMap[name] = bytes
                in imageExtensions -> imageMap[name] = bytes
            }
        }

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
        opf.spineIds.filter { id -> id !in opf.navItemIds }.forEach { id ->
            val href = opf.manifestMap[id] ?: return@forEach
            val fullPath = "$opfDir$href"
            val htmlContent = entryMap[fullPath] ?: entryMap[href] ?: return@forEach
            val result = HtmlChapterExtractor.parse(htmlContent)

            // Content before the chapter heading -> standalone chapter (no title)
            if (result.preHeadingParagraphs.isNotEmpty()) {
                chapters.add(Chapter(
                    index = chapters.size,
                    title = "",
                    paragraphs = result.preHeadingParagraphs,
                ))
            }

            // Main chapter: epigraph paragraphs first, then body paragraphs
            val allParagraphs = result.epigraphParagraphs + result.mainParagraphs
            if (result.headingTitle != null || allParagraphs.isNotEmpty()) {
                // The XHTML file's directory is the base for relative image src URLs.
                val contentDir = fullPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                val images = result.imageRefs.mapNotNull { ref ->
                    val bytes = resolveImageBytes(ref.src, contentDir, imageMap) ?: return@mapNotNull null
                    val name = "${bookHash}_${normalizePath(contentDir + decodeSrc(ref.src)).substringAfterLast('/')}"
                    ImageStore.save(context, bytes, name)?.let { ChapterImage(ref.anchorParagraph, it) }
                }
                chapters.add(Chapter(
                    index = chapters.size,
                    title = result.headingTitle ?: "Chapter ${chapters.size + 1}",
                    paragraphs = allParagraphs,
                    epigraphCount = result.epigraphParagraphs.size,
                    images = images,
                ))
            }
        }

        val coverPath = opf.coverHref?.let { href ->
            val fullCoverPath = if (opfDir.isEmpty()) href else "$opfDir$href"
            val coverBytes = resolveImageBytes(href, opfDir, imageMap)
                ?: imageMap[fullCoverPath]
            coverBytes?.let { saveCover(context, it, uri.toString(), fullCoverPath) }
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

    /** Resolves an image src to bytes in [imageMap], trying exact, case-insensitive, then basename match. */
    private fun resolveImageBytes(
        src: String,
        contentDir: String,
        imageMap: Map<String, ByteArray>,
    ): ByteArray? {
        val key = normalizePath(contentDir + decodeSrc(src))
        imageMap[key]?.let { return it }
        imageMap.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.let { return it.value }
        val basename = key.substringAfterLast('/')
        return imageMap.entries.firstOrNull { it.key.endsWith("/$basename", ignoreCase = true) }?.value
    }

    /** Writes cover [bytes] to filesDir/covers/<stableId>.<ext> and returns its absolute path. */
    private fun saveCover(context: Context, bytes: ByteArray, uriKey: String, coverEntryPath: String): String? =
        try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val ext = coverEntryPath.substringAfterLast('.', "jpg").lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
            val coverFile = File(coversDir, "${stableId(uriKey)}.$ext")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
}
