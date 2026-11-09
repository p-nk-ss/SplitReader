package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiCodecTest {
    @Test fun u32_highBitSet_isNonNegativeLong() {
        val b = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x01)
        assertEquals(0x80000001L, MobiCodec.u32(b, 0))
        assertTrue(MobiCodec.u32(b, 0) > 0)
    }

    @Test fun u16_reads_bigEndian() {
        assertEquals(0x0102, MobiCodec.u16(byteArrayOf(0x01, 0x02), 0))
    }

    @Test fun palmDoc_literalRun_roundTrips() {
        // 0x03 => copy next 3 literal bytes 'A','B','C'
        val input = byteArrayOf(0x03, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
        assertEquals("ABC", String(MobiCodec.palmDocDecompress(input, input.size), Charsets.US_ASCII))
    }

    @Test fun decompressText_joinsRecordsWithinBudget() {
        val records = listOf(ByteArray(0), "Hello ".toByteArray(), "world".toByteArray())
        val out = MobiCodec.decompressText(records::get, lastRecord = 2, compression = 1, extraDataFlags = 0, maxBytes = 1000)
        assertEquals("Hello world", String(out, Charsets.US_ASCII))
    }

    @Test fun decompressText_overBudget_throws() {
        // PalmDOC text expands ~5x (up to ~8x) from the file, so the file-size bound alone does not
        // bound it: the decompressed total has the same heap budget as everything else.
        val records = listOf(ByteArray(0)) + List(10) { ByteArray(400) { 'a'.code.toByte() } }
        org.junit.Assert.assertThrows(com.example.splitreader.domain.parser.util.BookTooLargeException::class.java) {
            MobiCodec.decompressText(records::get, lastRecord = 10, compression = 1, extraDataFlags = 0, maxBytes = 3000)
        }
    }

    @Test fun palmDoc_distanceZero_doesNotCorruptOrHang() {
        // A back-reference pair (0x80..0xBF) encoding distance 0 must be skipped, not copied.
        val input = byteArrayOf(0x41, 0x80.toByte(), 0x00) // 'A' then a distance-0 pair
        val out = MobiCodec.palmDocDecompress(input, input.size)
        assertEquals("A", String(out, Charsets.US_ASCII)) // no garbage appended, terminates
    }
}
