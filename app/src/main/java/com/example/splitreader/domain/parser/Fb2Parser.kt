package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.parser.fb2.Fb2Document
import com.example.splitreader.domain.parser.fb2.Fb2DocumentBuilder
import com.example.splitreader.domain.parser.fb2.Fb2Event
import com.example.splitreader.domain.parser.util.StagedFiles
import com.example.splitreader.domain.parser.util.stableId
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class Fb2Parser internal constructor(
    /** Name of the temp file a binary is staged in, next to its final [File]; unique per parse. A test seam. */
    private val tempNameFor: (target: File) -> String,
    private val crashReporter: CrashReporter = CrashReporter.NONE,
) : BookParser {

    @Inject constructor(crashReporter: CrashReporter) : this(StagedFiles.DEFAULT_TEMP_NAME, crashReporter)

    /** Built by hand (tests): nothing is reported. */
    constructor() : this(StagedFiles.DEFAULT_TEMP_NAME)

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
        val staged = StagedFiles(tempNameFor)
        StagedFiles.sweepStale(context.filesDir)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream")
        try {
            val doc = inputStream.use { readDocument(it, context, filePath, bookHash, staged) }
            if (doc.chapters.isEmpty()) throw IllegalStateException("No chapters found in fb2 file")
            val book = assembleBook(doc, filePath)
            staged.commit()
            staged.reportLost(crashReporter, "an FB2")
            return book
        } catch (e: Throwable) {
            // The final paths are deterministic per book URI: an owned book's cover and pictures
            // ARE those files, and FB2s are re-parsed on every open. A failed or cancelled parse
            // (CancellationException included -- rethrown below) therefore only removes its own
            // temp files; a first import that fails leaves nothing behind either.
            staged.rollback()
            throw e
        }
    }

    /**
     * Drives XmlPullParser, translating each event into an [Fb2Event] for the pure builder. Each
     * `<binary>` is decoded and written as soon as it is parsed -- see [Fb2DocumentBuilder]'s
     * `writeBinary` -- so only one image's bytes are ever held at once. It goes to a unique temp file
     * next to its final path (recorded in [staged]); the builder gets the FINAL path, which [parse]
     * fills by moving the temp file there once the whole parse has succeeded.
     */
    private suspend fun readDocument(
        input: InputStream,
        context: Context,
        filePath: String,
        bookHash: String,
        staged: StagedFiles,
    ): Fb2Document {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            .newPullParser().apply { setInput(input, null) }
        val builder = Fb2DocumentBuilder(
            writeBinary = { id, bytes, isCover ->
                if (bytes.isEmpty()) {
                    null
                } else {
                    // Staged only once fully written: a failed (e.g. disk-full) write must never be
                    // moved over the owned final file, nor fail the commit. A picture that cannot be
                    // written never breaks the parse.
                    val target = try {
                        if (isCover) coverFile(filePath, context) else ImageStore.targetFile(context, bytes, "${bookHash}_$id")
                    } catch (e: Exception) {
                        staged.noteLost(e)
                        null
                    }
                    target?.takeIf { t -> staged.stage(t) { it.writeBytes(bytes) } }?.absolutePath
                }
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

    /** The cover's final file, `filesDir/covers/<stableId>.jpg`, as before; creates the directory. */
    private fun coverFile(filePath: String, context: Context): File =
        File(File(context.filesDir, "covers").apply { mkdirs() }, "${stableId(filePath)}.jpg")
}
