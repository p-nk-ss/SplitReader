package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.parser.fb2.Fb2Document
import com.example.splitreader.domain.parser.fb2.Fb2DocumentBuilder
import com.example.splitreader.domain.parser.fb2.Fb2Event
import com.example.splitreader.domain.parser.util.stableId
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

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
        val filePath = uri.toString()
        val bookHash = stableId(filePath)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream")
        val doc = inputStream.use { readDocument(it, context, filePath, bookHash) }
        if (doc.chapters.isEmpty()) throw IllegalStateException("No chapters found in fb2 file")
        return assembleBook(doc, filePath)
    }

    /**
     * Drives XmlPullParser, translating each event into an [Fb2Event] for the pure builder. Each
     * `<binary>` is decoded and written to its final file (image or cover) as soon as it is parsed
     * -- see [Fb2DocumentBuilder]'s `writeBinary` -- so only one image's bytes are ever held at once.
     */
    private suspend fun readDocument(input: InputStream, context: Context, filePath: String, bookHash: String): Fb2Document {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            .newPullParser().apply { setInput(input, null) }
        val builder = Fb2DocumentBuilder(
            writeBinary = { id, bytes, isCover ->
                if (isCover) saveCoverBytes(bytes, filePath, context) else ImageStore.save(context, bytes, "${bookHash}_$id")
            },
        )
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            coroutineContext.ensureActive()
            when (eventType) {
                XmlPullParser.START_TAG -> builder.accept(
                    Fb2Event.Start(parser.name.lowercase(), hrefOf(parser), parser.getAttributeValue(null, "id")?.trim()),
                )
                XmlPullParser.TEXT -> builder.accept(Fb2Event.Text(parser.text ?: ""))
                XmlPullParser.END_TAG -> builder.accept(Fb2Event.End(parser.name?.lowercase() ?: ""))
            }
            eventType = parser.next()
        }
        return builder.finish()
    }

    /** First attribute whose name ends with "href" (handles href, l:href, xlink:href), '#' stripped. */
    private fun hrefOf(parser: XmlPullParser): String? =
        (0 until parser.attributeCount)
            .firstOrNull { parser.getAttributeName(it).endsWith("href") }
            ?.let { parser.getAttributeValue(it) }
            ?.removePrefix("#")?.trim()

    /** Binaries are already decoded and written to their final file by [readDocument]'s `writeBinary`;
     *  [Fb2Document.binaries] holds the saved path for each id, so no decoding happens here. */
    private fun assembleBook(doc: Fb2Document, filePath: String): Book {
        val chapters = doc.chapters.map { ch ->
            val images = ch.imageRefs.mapNotNull { (anchor, id) ->
                doc.binaries[id]?.let { path -> ChapterImage(anchor.coerceIn(0, ch.paragraphs.size), path) }
            }
            Chapter(
                index = ch.index,
                title = ch.title,
                paragraphs = ch.paragraphs,
                epigraphCount = ch.epigraphCount,
                images = images,
            )
        }
        val coverPath = doc.coverPath
        val synopsis = SynopsisExtractor.build(doc.annotation, chapters.flatMap { it.paragraphs })
        Log.d("FB2", "Parsed: title=${doc.title}, author=${doc.author}, chapters=${chapters.size}")
        return Book(
            title = doc.title,
            author = doc.author,
            chapters = chapters,
            filePath = filePath,
            coverPath = coverPath,
            synopsis = synopsis,
        )
    }

    /** Writes the cover's already-decoded [bytes] to `filesDir/covers/<stableId>.jpg`, as before. */
    private fun saveCoverBytes(bytes: ByteArray, filePath: String, context: Context): String? =
        try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val coverFile = File(coversDir, "${stableId(filePath)}.jpg")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
}
