package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipInputStream

class EpubParser constructor() : BookParser {

    private data class OpfData(
        val title: String,
        val author: String,
        val spineIds: List<String>,
        val manifestMap: Map<String, String>,
        val coverHref: String?,
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

        val chapters = opf.spineIds.mapIndexedNotNull { spineIndex, id ->
            val href = opf.manifestMap[id] ?: return@mapIndexedNotNull null
            val fullPath = "$opfDir$href"
            val htmlContent = entryMap[fullPath] ?: entryMap[href]
                ?: return@mapIndexedNotNull null
            parseHtmlChapter(spineIndex, htmlContent)
        }.mapIndexed { finalIndex, (headingTitle, paragraphs) ->
            Chapter(
                index = finalIndex,
                title = headingTitle ?: "Chapter ${finalIndex + 1}",
                paragraphs = paragraphs
            )
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
        val spineIds = doc.select("itemref").map { it.attr("idref") }

        // Cover detection: EPUB 3 properties, then EPUB 2 meta, then id fallback
        val coverHref = doc.selectFirst("item[properties~=cover-image]")?.attr("href")
            ?: run {
                val metaCoverId = doc.selectFirst("meta[name=cover]")?.attr("content")
                if (metaCoverId != null) {
                    doc.selectFirst("item[id=$metaCoverId]")?.attr("href")
                } else null
            }
            ?: doc.selectFirst("item[id=cover], item[id=cover-image]")?.attr("href")

        return OpfData(title, author, spineIds, manifestMap, coverHref)
    }

    private fun parseHtmlChapter(
        spineIndex: Int,
        content: ByteArray
    ): Pair<String?, List<String>>? {
        val doc = Jsoup.parse(content.inputStream(), "UTF-8", "")
        val headingTitle = doc.selectFirst("h1, h2")?.text()
        val paragraphs = doc.select("p").map { it.text() }.filter { it.isNotBlank() }
        return if (paragraphs.isEmpty()) null else Pair(headingTitle, paragraphs)
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
