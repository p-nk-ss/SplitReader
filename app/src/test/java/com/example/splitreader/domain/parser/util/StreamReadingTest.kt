package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** An InputStream that hands out at most one byte per read() call, to expose short reads. */
private class DripStream(bytes: ByteArray) : InputStream() {
    private val src = ByteArrayInputStream(bytes)
    override fun read(): Int = src.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        if (len == 0) 0 else src.read(b, off, 1) // never fills more than 1 byte
}

class StreamReadingTest {
    @Test fun readUpTo_fillsDespiteShortReads() {
        val data = ByteArray(256) { it.toByte() }
        val got = readUpTo(DripStream(data), 256)
        assertArrayEquals(data, got)
    }

    @Test fun readUpTo_truncatesAtEof() {
        val data = ByteArray(10) { it.toByte() }
        val got = readUpTo(DripStream(data), 256)
        assertEquals(10, got.size)
        assertArrayEquals(data, got)
    }

    @Test fun readAllBounded_returnsBytesWithinLimit() {
        val data = ByteArray(1000) { it.toByte() }
        assertArrayEquals(data, readAllBounded(ByteArrayInputStream(data), 2000))
    }

    @Test fun readAllBounded_throwsWhenExceeded() {
        val data = ByteArray(5000)
        assertThrows(BookTooLargeException::class.java) {
            readAllBounded(ByteArrayInputStream(data), 1024)
        }
    }
}
