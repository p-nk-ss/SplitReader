package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.parser.util.MAX_DECOMPRESSED
import com.example.splitreader.domain.parser.util.readAllBounded
import com.example.splitreader.domain.parser.util.stableId
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Parser for classic MOBI / PalmDOC e-books (MOBI 6, `.mobi`/`.prc`).
 *
 * Reads the PDB container ([MobiFile]), decodes the PalmDOC + MOBI + EXTH headers in record 0,
 * decompresses the text records (uncompressed or PalmDOC), then reuses [HtmlChapterExtractor]
 * on the resulting HTML — so chapter/epigraph handling matches EPUB.
 *
 * Out of scope (clear errors, no crash): HUFF/CDIC compression and AZW3/KF8 (a separate format),
 * and DRM-protected files. These can be added later as additional [BookParser]s.
 */
class MobiParser @Inject constructor() : BookParser {

    override val supportedExtensions = listOf("mobi", "prc", "azw")
    override val priority = 10

    override fun canParse(fileName: String, mimeType: String, header: ByteArray): Boolean =
        supportedExtensions.any { fileName.endsWith(".$it", ignoreCase = true) } ||
            mimeType.contains("mobi", ignoreCase = true) ||
            // PDB type+creator at offset 60: "BOOKMOBI" (MOBI) or "TEXtREAd" (plain PalmDOC).
            (header.size >= 68 && String(header, 60, 8, Charsets.US_ASCII).let {
                it == "BOOKMOBI" || it == "TEXtREAd"
            })

    override suspend fun parse(uri: Uri, context: Context): Book {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readAllBounded(it, MAX_DECOMPRESSED) }
            ?: throw IllegalStateException("Cannot open file")

        val pdb = MobiFile(bytes)
        if (pdb.recordCount < 1) throw IllegalStateException("Invalid MOBI: no records")
        val rec0 = pdb.record(0)

        // ── PalmDOC header (first 16 bytes of record 0) ──
        val compression = MobiCodec.u16(rec0, 0)          // 1 = none, 2 = PalmDOC, 17480 = HUFF/CDIC
        val textRecordCount = MobiCodec.u16(rec0, 8)
        val encryption = MobiCodec.u16(rec0, 12)
        if (encryption != 0) throw IllegalStateException("This MOBI is DRM-protected and can't be opened.")
        if (compression == 17480) {
            throw IllegalStateException("This MOBI uses HUFF/CDIC compression, which isn't supported yet.")
        }

        // ── MOBI header (starts at offset 16 of record 0), if present ──
        val hasMobiHeader = rec0.size >= 20 && String(rec0, 16, 4, Charsets.US_ASCII) == "MOBI"
        val mobiHeaderLength = if (hasMobiHeader) MobiCodec.u32(rec0, 20).toInt() else 0
        // File version at 0x24: 8 = KF8/AZW3, which we don't parse yet. A combo MOBI6+KF8 file keeps
        // a v6 header in record 0, so it falls through here and is read as plain MOBI6.
        val fileVersion = if (hasMobiHeader && rec0.size >= 40) MobiCodec.u32(rec0, 36).toInt() else 6
        if (fileVersion >= 8) {
            throw IllegalStateException("This looks like an AZW3/KF8 file, which isn't supported yet.")
        }
        val textEncoding = if (hasMobiHeader) MobiCodec.u32(rec0, 28).toInt() else 1252
        val charset: Charset = if (textEncoding == 65001) Charsets.UTF_8 else CP1252
        val firstImageIndex = if (hasMobiHeader && mobiHeaderLength >= 0x70) MobiCodec.u32(rec0, 108).toInt() else -1
        // "Extra data flags" mark trailing bytes appended to each text record (must be stripped).
        val extraDataFlags = if (hasMobiHeader && mobiHeaderLength >= 0xE4) MobiCodec.u16(rec0, 0xF2) else 0

        val exth = if (hasMobiHeader && (MobiCodec.u32(rec0, 0x80) and 0x40L) != 0L) {
            parseExth(rec0, 16 + mobiHeaderLength)
        } else emptyMap()

        // ── Decompress the text records into one HTML blob ──
        val textBytes = java.io.ByteArrayOutputStream()
        val lastTextRecord = textRecordCount.coerceAtMost(pdb.recordCount - 1)
        for (i in 1..lastTextRecord) {
            coroutineContext.ensureActive()
            val data = pdb.record(i)
            var size = data.size - MobiCodec.trailingDataSize(data, data.size, extraDataFlags)
            if (size < 0) size = 0
            val chunk = when (compression) {
                2 -> MobiCodec.palmDocDecompress(data, size)
                else -> data.copyOf(size) // compression == 1 (none)
            }
            textBytes.write(chunk)
        }
        val html = String(textBytes.toByteArray(), charset)

        // ── Metadata ──
        val fullName = if (hasMobiHeader) readFullName(rec0, charset) else null
        val title = exth[503] ?: fullName ?: pdbDisplayName(bytes) ?: "Unknown"
        val author = exth[100] ?: "Unknown"

        // ── Chapters: split on MOBI page breaks, then reuse the shared HTML extractor ──
        val chapters = buildChapters(html)
        if (chapters.isEmpty()) throw IllegalStateException("No readable text found in MOBI file")

        val coverPath = extractCover(pdb, exth, firstImageIndex, uri.toString(), context)

        val synopsis = SynopsisExtractor.build(exth[103], chapters.flatMap { it.paragraphs })

        Log.d("MOBI", "Parsed: title=$title, author=$author, chapters=${chapters.size}, comp=$compression")
        return Book(title, author, chapters, uri.toString(), coverPath, synopsis)
    }

    // ── Chapter building ─────────────────────────────────────────────────────

    // Inline illustrations are intentionally not supported for classic MOBI: the decompressed
    // PalmDOC text stream has no surviving <img> anchors (image records are addressed by index via
    // EXTH), so there is nothing to anchor into the chapter flow. Only the cover is extracted.
    private fun buildChapters(html: String): List<Chapter> {
        val fragments = MobiChapterSplitter.split(html)

        val chapters = mutableListOf<Chapter>()
        for (fragment in fragments) {
            val result = HtmlChapterExtractor.parse(fragment)
            if (result.isEmpty) continue

            if (result.preHeadingParagraphs.isNotEmpty()) {
                chapters.add(Chapter(
                    index = chapters.size,
                    title = "",
                    paragraphs = result.preHeadingParagraphs,
                ))
            }
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
        return chapters
    }

    // ── EXTH metadata ────────────────────────────────────────────────────────

    /** Parses the EXTH header at [start] in record 0, returning the record-type → string map. */
    private fun parseExth(rec0: ByteArray, start: Int): Map<Int, String> {
        if (start + 12 > rec0.size || String(rec0, start, 4, Charsets.US_ASCII) != "EXTH") return emptyMap()
        val count = MobiCodec.u32(rec0, start + 8).toInt()
        val out = HashMap<Int, String>()
        var p = start + 12
        repeat(count) {
            if (p + 8 > rec0.size) return@repeat
            val type = MobiCodec.u32(rec0, p).toInt()
            val len = MobiCodec.u32(rec0, p + 4).toInt()
            if (len < 8 || p + len > rec0.size) return@repeat
            val data = rec0.copyOfRange(p + 8, p + len)
            when (type) {
                100, 103, 503 -> out[type] = String(data, CP1252).trim()  // author, description, updated title
                201 -> out[type] = beInt(data).toString()                  // cover record offset
            }
            p += len
        }
        return out
    }

    // ── Cover extraction ─────────────────────────────────────────────────────

    private fun extractCover(
        pdb: MobiFile,
        exth: Map<Int, String>,
        firstImageIndex: Int,
        filePath: String,
        context: Context,
    ): String? {
        val coverOffset = exth[201]?.toIntOrNull() ?: return null
        if (firstImageIndex < 0) return null
        val recIndex = firstImageIndex + coverOffset
        if (recIndex !in 0 until pdb.recordCount) return null
        return try {
            val img = pdb.record(recIndex)
            val ext = when {
                img.size >= 3 && img[0].toInt() and 0xFF == 0xFF && img[1].toInt() and 0xFF == 0xD8 -> "jpg"
                img.size >= 4 && img[0].toInt() and 0xFF == 0x89 && img[1].toInt().toChar() == 'P' -> "png"
                img.size >= 3 && img[0].toInt().toChar() == 'G' && img[1].toInt().toChar() == 'I' -> "gif"
                else -> "jpg"
            }
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val hash = stableId(filePath)
            val coverFile = File(coversDir, "$hash.$ext")
            coverFile.writeBytes(img)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun readFullName(rec0: ByteArray, charset: Charset): String? {
        val off = MobiCodec.u32(rec0, 0x54).toInt()
        val len = MobiCodec.u32(rec0, 0x58).toInt()
        if (off <= 0 || len <= 0 || off + len > rec0.size) return null
        return String(rec0, off, len, charset).trim().ifBlank { null }
    }

    private fun pdbDisplayName(bytes: ByteArray): String? {
        if (bytes.size < 32) return null
        val end = (0 until 32).firstOrNull { bytes[it].toInt() == 0 } ?: 32
        return String(bytes, 0, end, Charsets.US_ASCII).trim().ifBlank { null }
    }

    private fun beInt(data: ByteArray): Int {
        var v = 0
        for (b in data) v = (v shl 8) or (b.toInt() and 0xFF)
        return v
    }

    companion object {
        private val CP1252: Charset = Charset.forName("windows-1252")
    }
}
