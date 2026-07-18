package com.example.splitreader.domain.parser

/**
 * Minimal reader for the Palm Database (PDB) container that MOBI/PRC files are built on.
 * Parses the 78-byte header and the record-offset table, exposing each record as a byte slice.
 *
 * Layout: 78-byte header, then [numRecords] 8-byte record-info entries (4-byte data offset +
 * 1-byte attributes + 3-byte unique id). Record *i* spans `[offset_i, offset_{i+1})`, the last
 * running to end-of-file.
 */
class MobiFile(private val bytes: ByteArray) {

    /** PDB type+creator, e.g. "BOOKMOBI" for MOBI, "TEXtREAd" for plain PalmDOC. */
    val typeCreator: String =
        if (bytes.size >= 68) String(bytes, 60, 8, Charsets.US_ASCII) else ""

    val recordCount: Int = if (bytes.size >= 78) MobiCodec.u16(bytes, 76) else 0

    private val recordOffsets: IntArray = IntArray(recordCount) { i -> MobiCodec.u32(bytes, 78 + i * 8).toInt() }

    /** Returns the raw bytes of record [index] (0-based). */
    fun record(index: Int): ByteArray {
        require(index in 0 until recordCount) { "record index $index out of range (count=$recordCount)" }
        val start = recordOffsets[index]
        val end = if (index + 1 < recordCount) recordOffsets[index + 1] else bytes.size
        return bytes.copyOfRange(start.coerceIn(0, bytes.size), end.coerceIn(start, bytes.size))
    }
}
