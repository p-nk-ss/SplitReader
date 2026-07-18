package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.parser.util.stableId
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class Fb2Parser @Inject constructor() : BookParser {

    override val supportedExtensions = listOf("fb2", "fb2.xml")
    override val priority = 5

    override fun canParse(fileName: String, mimeType: String, header: ByteArray): Boolean =
        fileName.endsWith(".fb2", ignoreCase = true) ||
            fileName.endsWith(".fb2.xml", ignoreCase = true) ||
            mimeType.contains("fb2", ignoreCase = true) ||
            mimeType == "text/xml" ||
            mimeType == "application/xml" ||
            String(header, Charsets.ISO_8859_1).contains("<FictionBook", ignoreCase = true)

    override suspend fun parse(uri: Uri, context: Context): Book {
        Log.d("FB2", "Starting parse, uri: $uri")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open file stream")
            Log.d("FB2", "Stream opened successfully")
            parseInternal(inputStream, uri.toString(), context)
        } catch (e: Exception) {
            Log.e("FB2", "CRASH in fb2 parser: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseInternal(inputStream: InputStream, filePath: String, context: Context): Book {
        var title = "Unknown Title"
        var firstName = ""
        var lastName = ""
        val chapters = mutableListOf<Chapter>()
        var chapterIndex = 0
        var insideBody = false
        var insideTitle = false
        var insideParagraph = false
        var currentText = StringBuilder()
        var insideAnnotation = false
        val annotationText = StringBuilder()

        // Sections nest arbitrarily in FB2: a "wrapper" section (e.g. a story/part) holds only
        // child sections + a title, while a "leaf" section directly holds <p> prose. We keep one
        // frame per open section so each leaf becomes its own chapter (with its own title) instead
        // of all children collapsing into the top-level section.
        val sectionStack = ArrayDeque<SectionFrame>()
        fun top() = sectionStack.lastOrNull()

        // Body-level epigraph (before first section); prepended to the first chapter emitted.
        val preambleParagraphs = mutableListOf<String>()
        var insidePreambleEpigraph = false
        var preambleFlushed = false

        // Section-level epigraph (inside a chapter section)
        var insideEpigraph = false
        var insideTextAuthor = false

        // Cover tracking
        var insideCoverPage = false
        var coverImageId: String? = null
        var insideCoverBinary = false
        var coverBinaryData: StringBuilder? = null

        // Inline illustrations: <image href="#id"> refs are discovered in the body, but the matching
        // <binary> base64 usually appears after </body>, so we capture referenced binaries during the
        // pass and decode/attach them after parsing. emitted-chapter-index -> list of (finalAnchor, id).
        val referencedImageIds = mutableSetOf<String>()
        val binaries = mutableMapOf<String, StringBuilder>()
        var currentBinaryId: String? = null
        val pendingChapterImages = mutableMapOf<Int, List<Pair<Int, String>>>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            coroutineContext.ensureActive()
            val tagName = parser.name?.lowercase() ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> when {
                    tagName == "book-title" -> currentText = StringBuilder()
                    tagName == "first-name" -> currentText = StringBuilder()
                    tagName == "last-name" -> currentText = StringBuilder()
                    tagName == "coverpage" -> insideCoverPage = true
                    tagName == "image" && insideCoverPage && coverImageId == null -> {
                        // Find any attribute ending with "href" (handles l:href, xlink:href, href)
                        val href = (0 until parser.attributeCount)
                            .firstOrNull { parser.getAttributeName(it).endsWith("href") }
                            ?.let { parser.getAttributeValue(it) }
                        coverImageId = href?.removePrefix("#")?.trim()
                    }
                    tagName == "image" && !insideCoverPage && sectionStack.isNotEmpty() -> {
                        val href = (0 until parser.attributeCount)
                            .firstOrNull { parser.getAttributeName(it).endsWith("href") }
                            ?.let { parser.getAttributeValue(it) }
                        val id = href?.removePrefix("#")?.trim()
                        if (!id.isNullOrEmpty()) {
                            top()?.let { it.imageRefs.add(it.directParagraphs.size to id) }
                            referencedImageIds.add(id)
                        }
                    }
                    tagName == "binary" -> {
                        val id = parser.getAttributeValue(null, "id")?.trim()
                        if (id != null) {
                            if (id == coverImageId) {
                                insideCoverBinary = true
                                coverBinaryData = StringBuilder()
                            }
                            if (id in referencedImageIds) {
                                currentBinaryId = id
                                binaries[id] = StringBuilder()
                            }
                        }
                    }
                    tagName == "body" -> insideBody = true
                    tagName == "annotation" -> insideAnnotation = true
                    tagName == "epigraph" && insideBody && sectionStack.isEmpty() -> insidePreambleEpigraph = true
                    tagName == "epigraph" && insideBody && sectionStack.isNotEmpty() -> insideEpigraph = true
                    tagName == "text-author" && insideEpigraph -> {
                        insideTextAuthor = true
                        currentText = StringBuilder()
                    }
                    tagName == "section" && insideBody -> {
                        sectionStack.addLast(SectionFrame(chapterIndex))
                        if (sectionStack.size == 1) insideEpigraph = false
                    }
                    tagName == "title" && sectionStack.isNotEmpty() -> {
                        insideTitle = true
                        currentText = StringBuilder()
                    }
                    tagName == "p" && (sectionStack.isNotEmpty() || insidePreambleEpigraph) && !insideTitle -> {
                        insideParagraph = true
                        currentText = StringBuilder()
                    }
                }

                XmlPullParser.END_TAG -> when {
                    tagName == "book-title" -> title = currentText.toString().trim()
                    tagName == "first-name" -> firstName = currentText.toString().trim()
                    tagName == "last-name" -> lastName = currentText.toString().trim()
                    tagName == "coverpage" -> insideCoverPage = false
                    tagName == "epigraph" && insidePreambleEpigraph -> insidePreambleEpigraph = false
                    tagName == "epigraph" && insideEpigraph -> insideEpigraph = false
                    tagName == "text-author" && insideTextAuthor -> {
                        insideTextAuthor = false
                        val text = currentText.toString().trim()
                        if (text.isNotBlank()) top()?.epigraphParagraphs?.add(text)
                    }
                    tagName == "binary" -> {
                        insideCoverBinary = false
                        currentBinaryId = null
                    }
                    tagName == "title" && sectionStack.isNotEmpty() -> {
                        insideTitle = false
                        top()?.let { it.title = currentText.toString().trim().ifBlank { it.title } }
                    }
                    tagName == "p" && insideParagraph -> {
                        insideParagraph = false
                        val text = currentText.toString().trim()
                        if (text.isNotBlank() && text.length < 5000) {
                            when {
                                insidePreambleEpigraph -> preambleParagraphs.add(text)
                                insideEpigraph -> top()?.epigraphParagraphs?.add(text)
                                else -> top()?.directParagraphs?.add(text)
                            }
                        }
                    }
                    tagName == "section" && sectionStack.isNotEmpty() -> {
                        val frame = sectionStack.removeLast()
                        // Leaf section (held its own prose) → emit a chapter; wrapper sections
                        // (only subsections) emit nothing, their children already did.
                        if (frame.directParagraphs.isNotEmpty()) {
                            var preShift = 0
                            if (!preambleFlushed && preambleParagraphs.isNotEmpty()) {
                                preShift = preambleParagraphs.size
                                frame.directParagraphs.addAll(0, preambleParagraphs)
                                preambleParagraphs.clear()
                                preambleFlushed = true
                            }
                            val allParagraphs = frame.epigraphParagraphs + frame.directParagraphs
                            if (allParagraphs.isNotEmpty()) {
                                // Prefix ancestor wrapper titles (e.g. story/part name) so each
                                // chapter stays self-describing: "Цена риска · 1. ...".
                                val prefix = sectionStack
                                    .filter { it.title != "Chapter ${it.index + 1}" }
                                    .joinToString("") { "${it.title} · " }
                                chapters.add(Chapter(
                                    index = chapterIndex,
                                    title = prefix + frame.title,
                                    paragraphs = allParagraphs,
                                    epigraphCount = frame.epigraphParagraphs.size,
                                ))
                                if (frame.imageRefs.isNotEmpty()) {
                                    val epiShift = frame.epigraphParagraphs.size
                                    pendingChapterImages[chapterIndex] =
                                        frame.imageRefs.map { (a, id) -> (epiShift + preShift + a) to id }
                                }
                                chapterIndex++
                            }
                        }
                        if (sectionStack.isEmpty()) insideEpigraph = false
                    }
                    tagName == "body" -> insideBody = false
                    tagName == "annotation" -> insideAnnotation = false
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    when {
                        insideCoverBinary -> coverBinaryData?.append(text)
                        currentBinaryId != null -> binaries[currentBinaryId]?.append(text)
                        insideAnnotation -> annotationText.append(text).append(' ')
                        insideParagraph || insideTitle || insideTextAuthor || !insideBody ->
                            currentText.append(text)
                    }
                }
            }
            eventType = parser.next()
        }

        val author = "$firstName $lastName".trim().ifBlank { "Unknown Author" }
        Log.d("FB2", "Parsed: title=$title, author=$author, chapters=${chapters.size}")

        if (chapters.isEmpty()) throw IllegalStateException("No chapters found in fb2 file")

        // All <binary> blobs are now known: decode referenced inline images and attach by chapter.
        if (pendingChapterImages.isNotEmpty()) {
            val bookHash = stableId(filePath)
            pendingChapterImages.forEach { (chIdx, refs) ->
                val i = chapters.indexOfFirst { it.index == chIdx }
                if (i < 0) return@forEach
                val maxAnchor = chapters[i].paragraphs.size
                val resolved = refs.mapNotNull { (anchor, id) ->
                    val raw = binaries[id]?.toString()?.replace("\\s".toRegex(), "")
                    if (raw.isNullOrEmpty()) return@mapNotNull null
                    val bytes = try { Base64.decode(raw, Base64.DEFAULT) } catch (_: Exception) { return@mapNotNull null }
                    ImageStore.save(context, bytes, "${bookHash}_$id")
                        ?.let { ChapterImage(anchor.coerceIn(0, maxAnchor), it) }
                }
                if (resolved.isNotEmpty()) chapters[i] = chapters[i].copy(images = resolved)
            }
        }

        val coverPath = saveFb2Cover(coverBinaryData, filePath, context)
        val synopsis = SynopsisExtractor.build(
            annotationText.toString(),
            chapters.flatMap { it.paragraphs },
        )
        return Book(title = title, author = author, chapters = chapters, filePath = filePath, coverPath = coverPath, synopsis = synopsis)
    }

    private fun saveFb2Cover(
        coverBinaryData: StringBuilder?,
        filePath: String,
        context: Context,
    ): String? {
        val raw = coverBinaryData?.toString()?.replace("\\s".toRegex(), "")
        if (raw.isNullOrEmpty()) return null
        return try {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val coversDir = File(context.filesDir, "covers")
            coversDir.mkdirs()
            val hash = stableId(filePath)
            val coverFile = File(coversDir, "$hash.jpg")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** One open `<section>` while parsing. A leaf (own [directParagraphs]) becomes a chapter. */
    private class SectionFrame(val index: Int) {
        var title: String = "Chapter ${index + 1}"
        val directParagraphs = mutableListOf<String>()
        val epigraphParagraphs = mutableListOf<String>()
        /** (anchor within directParagraphs, binary id) for inline <image> refs in this section. */
        val imageRefs = mutableListOf<Pair<Int, String>>()
    }
}
