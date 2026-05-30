package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream

class Fb2Parser constructor() : BookParser {

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

    private fun parseInternal(inputStream: InputStream, filePath: String, context: Context): Book {
        var title = "Unknown Title"
        var firstName = ""
        var lastName = ""
        val chapters = mutableListOf<Chapter>()
        var currentChapterTitle = ""
        val currentParagraphs = mutableListOf<String>()
        var chapterIndex = 0
        var insideBody = false
        var sectionDepth = 0
        var insideTitle = false
        var insideParagraph = false
        var currentText = StringBuilder()

        // Body-level epigraph (before first section)
        val preambleParagraphs = mutableListOf<String>()
        var insidePreambleEpigraph = false

        // Section-level epigraph (inside a chapter section)
        val currentEpigraphParagraphs = mutableListOf<String>()
        var insideEpigraph = false
        var insideTextAuthor = false

        // Cover tracking
        var insideCoverPage = false
        var coverImageId: String? = null
        var insideCoverBinary = false
        var coverBinaryData: StringBuilder? = null

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
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
                    tagName == "binary" -> {
                        val id = parser.getAttributeValue(null, "id")?.trim()
                        if (id != null && id == coverImageId) {
                            insideCoverBinary = true
                            coverBinaryData = StringBuilder()
                        }
                    }
                    tagName == "body" -> insideBody = true
                    tagName == "epigraph" && insideBody && sectionDepth == 0 -> insidePreambleEpigraph = true
                    tagName == "epigraph" && insideBody && sectionDepth > 0 -> insideEpigraph = true
                    tagName == "text-author" && insideEpigraph -> {
                        insideTextAuthor = true
                        currentText = StringBuilder()
                    }
                    tagName == "section" && insideBody -> {
                        sectionDepth++
                        if (sectionDepth == 1) {
                            currentChapterTitle = "Chapter ${chapterIndex + 1}"
                            currentParagraphs.clear()
                            currentEpigraphParagraphs.clear()
                            insideEpigraph = false
                            if (preambleParagraphs.isNotEmpty()) {
                                currentParagraphs.addAll(preambleParagraphs)
                                preambleParagraphs.clear()
                            }
                        }
                    }
                    tagName == "title" && sectionDepth > 0 -> {
                        insideTitle = true
                        currentText = StringBuilder()
                    }
                    tagName == "p" && (sectionDepth > 0 || insidePreambleEpigraph) && !insideTitle -> {
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
                        if (text.isNotBlank()) currentEpigraphParagraphs.add(text)
                    }
                    tagName == "binary" -> insideCoverBinary = false
                    tagName == "title" && sectionDepth > 0 -> {
                        insideTitle = false
                        currentChapterTitle = currentText.toString().trim()
                            .ifBlank { "Chapter ${chapterIndex + 1}" }
                    }
                    tagName == "p" && insideParagraph -> {
                        insideParagraph = false
                        val text = currentText.toString().trim()
                        if (text.isNotBlank() && text.length < 5000) {
                            when {
                                insidePreambleEpigraph -> preambleParagraphs.add(text)
                                insideEpigraph -> currentEpigraphParagraphs.add(text)
                                else -> currentParagraphs.add(text)
                            }
                        }
                    }
                    tagName == "section" && sectionDepth > 0 -> {
                        sectionDepth--
                        if (sectionDepth == 0) {
                            val allParagraphs = currentEpigraphParagraphs + currentParagraphs
                            if (allParagraphs.isNotEmpty()) {
                                chapters.add(Chapter(
                                    index = chapterIndex,
                                    title = currentChapterTitle,
                                    paragraphs = allParagraphs,
                                    epigraphCount = currentEpigraphParagraphs.size,
                                ))
                                chapterIndex++
                            }
                            currentParagraphs.clear()
                            currentEpigraphParagraphs.clear()
                        }
                    }
                    tagName == "body" -> insideBody = false
                }

                XmlPullParser.TEXT -> {
                    when {
                        insideCoverBinary -> coverBinaryData?.append(parser.text ?: "")
                        insideParagraph || insideTitle || insideTextAuthor || !insideBody ->
                            currentText.append(parser.text ?: "")
                    }
                }
            }
            eventType = parser.next()
        }

        val author = "$firstName $lastName".trim().ifBlank { "Unknown Author" }
        Log.d("FB2", "Parsed: title=$title, author=$author, chapters=${chapters.size}")

        if (chapters.isEmpty()) throw IllegalStateException("No chapters found in fb2 file")

        val coverPath = saveFb2Cover(coverBinaryData, filePath, context)
        return Book(title = title, author = author, chapters = chapters, filePath = filePath, coverPath = coverPath)
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
            val hash = filePath.hashCode().toLong().and(0x7FFFFFFFL)
            val coverFile = File(coversDir, "$hash.jpg")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
