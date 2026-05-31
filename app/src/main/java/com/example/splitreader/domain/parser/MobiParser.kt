package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject

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

    override fun canParse(fileName: String, mimeType: String, header: ByteArray): Boolean =
        supportedExtensions.any { fileName.endsWith(".$it", ignoreCase = true) } ||
            mimeType.contains("mobi", ignoreCase = true) ||
            // PDB type+creator at offset 60: "BOOKMOBI" (MOBI) or "TEXtREAd" (plain PalmDOC).
            (header.size >= 68 && String(header, 60, 8, Charsets.US_ASCII).let {
                it == "BOOKMOBI" || it == "TEXtREAd"
            })

    override suspend fun parse(uri: Uri, context: Context): Book {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")

        val pdb = MobiFile(bytes)
        if (pdb.recordCount < 1) throw IllegalStateException("Invalid MOBI: no records")
        val rec0 = pdb.record(0)

        // ── PalmDOC header (first 16 bytes of record 0) ──
        val compression = u16(rec0, 0)          // 1 = none, 2 = PalmDOC, 17480 = HUFF/CDIC
        val textRecordCount = u16(rec0, 8)
        val encryption = u16(rec0, 12)
        if (encryption != 0) throw IllegalStateException("This MOBI is DRM-protected and can't be opened.")
        if (compression == 17480) {
            throw IllegalStateException("This MOBI uses HUFF/CDIC compression, which isn't supported yet.")
        }

        // ── MOBI header (starts at offset 16 of record 0), if present ──
        val hasMobiHeader = rec0.size >= 20 && String(rec0, 16, 4, Charsets.US_ASCII) == "MOBI"
        val mobiHeaderLength = if (hasMobiHeader) u32(rec0, 20) else 0
        // File version at 0x24: 8 = KF8/AZW3, which we don't parse yet. A combo MOBI6+KF8 file keeps
        // a v6 header in record 0, so it falls through here and is read as plain MOBI6.
        val fileVersion = if (hasMobiHeader && rec0.size >= 40) u32(rec0, 36) else 6
        if (fileVersion >= 8) {
            throw IllegalStateException("This looks like an AZW3/KF8 file, which isn't supported yet.")
        }
        val textEncoding = if (hasMobiHeader) u32(rec0, 28) else 1252
        val charset: Charset = if (textEncoding == 65001) Charsets.UTF_8 else CP1252
        val firstImageIndex = if (hasMobiHeader && mobiHeaderLength >= 0x70) u32(rec0, 108) else -1
        // "Extra data flags" mark trailing bytes appended to each text record (must be stripped).
        val extraDataFlags = if (hasMobiHeader && mobiHeaderLength >= 0xE4) u16(rec0, 0xF2) else 0

        val exth = if (hasMobiHeader && (u32(rec0, 0x80) and 0x40) != 0) {
            parseExth(rec0, 16 + mobiHeaderLength)
        } else emptyMap()

        // ── Decompress the text records into one HTML blob ──
        val textBytes = java.io.ByteArrayOutputStream()
        val lastTextRecord = textRecordCount.coerceAtMost(pdb.recordCount - 1)
        for (i in 1..lastTextRecord) {
            val data = pdb.record(i)
            var size = data.size - trailingDataSize(data, data.size, extraDataFlags)
            if (size < 0) size = 0
            val chunk = when (compression) {
                2 -> palmDocDecompress(data, size)
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

        Log.d("MOBI", "Parsed: title=$title, author=$author, chapters=${chapters.size}, comp=$compression")
        return Book(title, author, chapters, uri.toString(), coverPath)
    }

    // ── Chapter building ─────────────────────────────────────────────────────

    private fun buildChapters(html: String): List<Chapter> {
        val fragments = PAGEBREAK.split(html).map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(html) }

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
        val count = u32(rec0, start + 8)
        val out = HashMap<Int, String>()
        var p = start + 12
        repeat(count) {
            if (p + 8 > rec0.size) return@repeat
            val type = u32(rec0, p)
            val len = u32(rec0, p + 4)
            if (len < 8 || p + len > rec0.size) return@repeat
            val data = rec0.copyOfRange(p + 8, p + len)
            when (type) {
                100, 503 -> out[type] = String(data, CP1252).trim()  // author, updated title
                201 -> out[type] = beInt(data).toString()            // cover record offset
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
            val hash = filePath.hashCode().toLong().and(0x7FFFFFFFL)
            val coverFile = File(coversDir, "$hash.$ext")
            coverFile.writeBytes(img)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun readFullName(rec0: ByteArray, charset: Charset): String? {
        val off = u32(rec0, 0x54)
        val len = u32(rec0, 0x58)
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

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    /**
     * Size of the trailing data entries appended to a text record, which must be stripped before
     * decompression. Ported from the MOBI spec / kindleunpack `getSizeOfTrailingDataEntries`.
     */
    private fun trailingDataSize(data: ByteArray, size: Int, flags: Int): Int {
        fun entrySize(end: Int): Int {
            var bitpos = 0
            var result = 0
            var pos = end
            if (pos <= 0) return 0
            while (true) {
                val v = data[pos - 1].toInt() and 0xFF
                result = result or ((v and 0x7F) shl bitpos)
                bitpos += 7
                pos -= 1
                if (v and 0x80 != 0 || bitpos >= 28 || pos == 0) return result
            }
        }
        var num = 0
        var testFlags = flags shr 1
        while (testFlags != 0) {
            if (testFlags and 1 == 1) num += entrySize(size - num)
            testFlags = testFlags shr 1
        }
        if (flags and 1 == 1 && size - num - 1 >= 0) {
            num += (data[size - num - 1].toInt() and 0x3) + 1
        }
        return num
    }

    /** PalmDOC (LZ77 variant) decompression. */
    private fun palmDocDecompress(input: ByteArray, length: Int): ByteArray {
        var out = ByteArray(maxOf(64, length * 8))
        var outLen = 0
        fun put(b: Int) {
            if (outLen >= out.size) out = out.copyOf(out.size * 2)
            out[outLen++] = b.toByte()
        }
        var i = 0
        while (i < length) {
            val c = input[i].toInt() and 0xFF
            i++
            when {
                c == 0x00 -> put(0)
                c in 0x01..0x08 -> {
                    var j = 0
                    while (j < c && i < length) { put(input[i].toInt() and 0xFF); i++; j++ }
                }
                c in 0x09..0x7F -> put(c)
                c in 0x80..0xBF -> {
                    if (i < length) {
                        val c2 = input[i].toInt() and 0xFF
                        i++
                        val pair = ((c shl 8) or c2) and 0x3FFF
                        val distance = pair shr 3
                        val copyLen = (pair and 0x07) + 3
                        var src = outLen - distance
                        if (src >= 0) {
                            var k = 0
                            while (k < copyLen) { put(out[src].toInt() and 0xFF); src++; k++ }
                        }
                    }
                }
                else -> { put(' '.code); put(c xor 0x80) } // 0xC0..0xFF: space + (c & 0x7F)
            }
        }
        return out.copyOf(outLen)
    }

    companion object {
        private val CP1252: Charset = Charset.forName("windows-1252")
        private val PAGEBREAK = Regex("<\\s*mbp:pagebreak[^>]*>", RegexOption.IGNORE_CASE)
    }
}
