package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipInputStream

/** Substring markers used to heuristically detect epigraph/verse blocks by CSS class name. */
private val EPIGRAPH_KEYWORDS = listOf(
    "epigraph", "epigraf",
    "poem", "stih", "verse", "stanza",
    "quote", "cite", "citation",
    "litany", "poetry",
)

class EpubParser : BookParser {

    private data class OpfData(
        val title: String,
        val author: String,
        val spineIds: List<String>,
        val manifestMap: Map<String, String>,
        val coverHref: String?,
        val navItemIds: Set<String>,
    )

    override suspend fun parse(uri: Uri, context: Context): Book {
        val entryMap = mutableMapOf<String, ByteArray>()
        val textExtensions = setOf("xml", "html", "xhtml", "opf", "ncx", "htm")

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory &&
                        entry.name.substringAfterLast('.').lowercase() in textExtensions
                    ) {
                        entryMap[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Cannot open file")

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

        val chapters = mutableListOf<Chapter>()
        opf.spineIds.filter { id -> id !in opf.navItemIds }.forEach { id ->
            val href = opf.manifestMap[id] ?: return@forEach
            val fullPath = "$opfDir$href"
            val htmlContent = entryMap[fullPath] ?: entryMap[href] ?: return@forEach
            val result = parseHtmlChapter(htmlContent)

            // Content before the chapter heading → standalone chapter (no title)
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
                chapters.add(Chapter(
                    index = chapters.size,
                    title = result.headingTitle ?: "Chapter ${chapters.size + 1}",
                    paragraphs = allParagraphs,
                    epigraphCount = result.epigraphParagraphs.size,
                ))
            }
        }

        val coverPath = opf.coverHref?.let { href ->
            val fullCoverPath = if (opfDir.isEmpty()) href else "$opfDir$href"
            extractCoverFromZip(uri, context, fullCoverPath)
        }

        return Book(opf.title, opf.author, chapters, uri.toString(), coverPath)
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

        return OpfData(title, author, spineIds, manifestMap, coverHref, navItemIds)
    }

    private data class HtmlParseResult(
        val headingTitle: String?,
        val preHeadingParagraphs: List<String>,
        val epigraphParagraphs: List<String>,
        val mainParagraphs: List<String>,
    )

    private fun parseHtmlChapter(content: ByteArray): HtmlParseResult {
        val doc = Jsoup.parse(content.inputStream(), "UTF-8", "")
        val body = doc.body()
            ?: return HtmlParseResult(null, emptyList(), emptyList(), emptyList())
        val headingEl = body.selectFirst("h1, h2, h3")
        val headingTitle = headingEl?.text()?.takeIf { it.isNotBlank() }

        if (headingEl == null) {
            val paras = body.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
                .ifEmpty {
                    body.select("div, section, blockquote")
                        .map { it.ownText().trim() }.filter { it.length > 20 }
                }
            return HtmlParseResult(null, emptyList(), emptyList(), paras)
        }

        val preHeading = mutableListOf<String>()
        val epigraph = mutableListOf<String>()
        val main = mutableListOf<String>()
        var foundHeading = false

        for (el in body.select("h1, h2, h3, blockquote, p")) {
            val tag = el.tagName()
            when {
                tag in listOf("h1", "h2", "h3") -> foundHeading = true
                !foundHeading && tag == "p" && el.closest("blockquote") == null -> {
                    val text = el.text().trim()
                    if (text.isNotBlank()) preHeading.add(text)
                }
                tag == "blockquote" && foundHeading -> {
                    val texts = el.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
                        .ifEmpty { listOf(el.text().trim()).filter { it.isNotBlank() } }
                    epigraph.addAll(texts)
                }
                tag == "p" && foundHeading && el.closest("blockquote") == null -> {
                    val text = el.text().trim()
                    if (text.isBlank()) continue
                    if (looksLikeEpigraph(el)) epigraph.add(text) else main.add(text)
                }
            }
        }

        return HtmlParseResult(headingTitle, preHeading, epigraph, main)
    }

    private fun looksLikeEpigraph(el: org.jsoup.nodes.Element): Boolean {
        fun org.jsoup.nodes.Element.hasEpigraphClass() =
            classNames().any { c -> EPIGRAPH_KEYWORDS.any { k -> c.lowercase().contains(k) } }

        if (el.hasEpigraphClass()) return true
        val style = el.attr("style").lowercase()
        if ("italic" in style) return true
        // Check ancestor containers up to but not including body
        for (parent in el.parents()) {
            if (parent.tagName() in listOf("body", "html")) break
            if (parent.hasEpigraphClass()) return true
        }
        return false
    }

    private fun extractCoverFromZip(uri: Uri, context: Context, coverEntryPath: String): String? {
        return try {
            val coversDir = File(context.filesDir, "covers")
            coversDir.mkdirs()
            val ext = coverEntryPath.substringAfterLast('.', "jpg").lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
            val hash = uri.toString().hashCode().toLong().and(0x7FFFFFFFL)
            val coverFile = File(coversDir, "$hash.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == coverEntryPath ||
                            entry.name.endsWith("/$coverEntryPath")
                        ) {
                            coverFile.outputStream().use { zip.copyTo(it) }
                            return coverFile.absolutePath
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
