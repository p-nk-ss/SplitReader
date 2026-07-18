package com.example.splitreader.domain.parser

/**
 * Pure byte-level primitives for classic MOBI / PalmDOC, extracted from MobiParser so they can be
 * unit-tested on the JVM. [u32] returns Long to avoid the sign overflow that a 32-bit header field
 * with the high bit set would cause when used as a size/offset.
 */
object MobiCodec {

    fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    /**
     * Size of the trailing data entries appended to a text record, stripped before decompression.
     * Ported from the MOBI spec / kindleunpack `getSizeOfTrailingDataEntries`.
     */
    fun trailingDataSize(data: ByteArray, size: Int, flags: Int): Int {
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

    /** PalmDOC (LZ77 variant) decompression. A back-reference with distance 0 is skipped (guard). */
    fun palmDocDecompress(input: ByteArray, length: Int): ByteArray {
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
                        if (distance > 0 && src >= 0) {
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
}
