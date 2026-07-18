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

    @Test fun palmDoc_distanceZero_doesNotCorruptOrHang() {
        // A back-reference pair (0x80..0xBF) encoding distance 0 must be skipped, not copied.
        val input = byteArrayOf(0x41, 0x80.toByte(), 0x00) // 'A' then a distance-0 pair
        val out = MobiCodec.palmDocDecompress(input, input.size)
        assertEquals("A", String(out, Charsets.US_ASCII)) // no garbage appended, terminates
    }
}
