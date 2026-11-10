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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class Fb2Parser internal constructor(
    /** Name of the temp file a binary is staged in, next to its final [File]; unique per parse. A test seam. */
    private val tempNameFor: (target: File) -> String,
) : BookParser {

    @Inject constructor() : this({ target -> "${target.name}$TEMP_MARKER${UUID.randomUUID()}" })

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
        val staged = mutableListOf<StagedFile>()
        sweepStaleTemps(context)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream")
        try {
            val doc = inputStream.use { readDocument(it, context, filePath, bookHash, staged) }
            if (doc.chapters.isEmpty()) throw IllegalStateException("No chapters found in fb2 file")
            val book = assembleBook(doc, filePath)
            commit(staged)
            return book
        } catch (e: Throwable) {
            // The final paths are deterministic per book URI: an owned book's cover and pictures
            // ARE those files, and FB2s are re-parsed on every open. A failed or cancelled parse
            // (CancellationException included -- rethrown below) therefore only removes its own
            // temp files; a first import that fails leaves nothing behind either.
            deleteTemps(staged)
            throw e
        }
    }

    /** One binary written by this parse to [temp], to be moved to [target] only if the parse succeeds. */
    private class StagedFile(val temp: File, val target: File)

    /** Moves every staged file over its final path (atomic rename, replacing the old file). */
    private fun commit(staged: List<StagedFile>) {
        for (s in staged) {
            try {
                Files.move(s.temp.toPath(), s.target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(s.temp.toPath(), s.target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
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
        staged: MutableList<StagedFile>,
    ): Fb2Document {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            .newPullParser().apply { setInput(input, null) }
        val builder = Fb2DocumentBuilder(
            writeBinary = { id, bytes, isCover ->
                if (bytes.isEmpty()) {
                    null
                } else {
                    var temp: File? = null
                    try {
                        val target = if (isCover) coverFile(filePath, context) else ImageStore.targetFile(context, bytes, "${bookHash}_$id")
                        temp = File(target.parentFile, tempNameFor(target))
                        temp.writeBytes(bytes)
                        // Staged only once fully written: a failed (e.g. disk-full) write must never
                        // be moved over the owned final file, nor fail the commit.
                        staged.add(StagedFile(temp, target))
                        target.absolutePath
                    } catch (_: Exception) {
                        try { temp?.delete() } catch (_: Exception) { /* best-effort */ }
                        null // a picture that cannot be written never breaks the parse
                    }
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

    /** Best-effort delete of this parse's temp files (never a final path) -- see [parse]. */
    private fun deleteTemps(staged: List<StagedFile>) {
        for (s in staged) {
            try { s.temp.delete() } catch (_: Exception) { /* best-effort */ }
        }
    }

    /** The cover's final file, `filesDir/covers/<stableId>.jpg`, as before; creates the directory. */
    private fun coverFile(filePath: String, context: Context): File =
        File(File(context.filesDir, "covers").apply { mkdirs() }, "${stableId(filePath)}.jpg")

    /**
     * Deletes `*.part-*` temp files in covers/ and images/ older than [STALE_TEMP_MILLIS] -- left by
     * a parse the process died in. A running parse's temps are fresh, so they are never touched.
     * Plain files only; a symbolic link is left alone. Best-effort.
     */
    private fun sweepStaleTemps(context: Context, now: Long = System.currentTimeMillis()) {
        for (dirName in listOf("covers", "images")) {
            val children = try { File(context.filesDir, dirName).listFiles() } catch (_: Exception) { null } ?: continue
            for (f in children) {
                if (!TEMP_NAME_PATTERN.containsMatchIn(f.name)) continue
                try {
                    if (Files.isSymbolicLink(f.toPath()) || !f.isFile) continue
                    if (now - f.lastModified() > STALE_TEMP_MILLIS) f.delete()
                } catch (_: Exception) { /* best-effort */ }
            }
        }
    }

    private companion object {
        /** Marks a staged binary's temp file: `<final name>.part-<unique>`. */
        const val TEMP_MARKER = ".part-"
        const val STALE_TEMP_MILLIS = 60 * 60 * 1000L

        /**
         * Matches only a real temp name's suffix: `.part-<UUID>` (the exact shape the default
         * [tempNameFor] produces), anchored at the end of the name. A final picture file whose fb2
         * binary id happens to contain the literal substring ".part-" (e.g. an id like "pic.part-1")
         * must NOT match -- it is an owned file, never a temp one.
         */
        val TEMP_NAME_PATTERN = Regex("\\.part-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
