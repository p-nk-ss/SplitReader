package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

/**
 * An effectively-infinite stream of zero bytes that counts how many it actually served. Used to
 * prove a bounded read stops shortly after the budget instead of draining a huge/zip-bomb stream.
 */
private class CountingInfiniteStream : InputStream() {
    var bytesServed = 0L
    override fun read(): Int {
        bytesServed++
        return 0
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        bytesServed += len
        return len
    }
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

    @Test fun readAllBounded_stopsShortlyAfterBudget_neverDrainsTheRest() {
        // Simulates a book far bigger than memory: without this, growing a ByteArrayOutputStream
        // past the budget (or draining the whole stream before checking) is what OOMs a
        // constrained heap before the "too large" guard ever gets a chance to fire.
        val stream = CountingInfiniteStream()
        val maxBytes = 10_000L
        assertThrows(BookTooLargeException::class.java) {
            readAllBounded(stream, maxBytes)
        }
        // Stops within a single read chunk past the budget -- never reads anywhere close to "the
        // rest" of an effectively infinite stream.
        assertTrue(stream.bytesServed <= maxBytes + 8 * 1024)
    }
}
