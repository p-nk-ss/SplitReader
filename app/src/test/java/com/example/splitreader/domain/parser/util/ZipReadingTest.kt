package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
        }
    }
    return bos.toByteArray()
}

class ZipReadingTest {
    @Test fun readsOnlyAcceptedEntries() {
        val zip = zipOf("a.xhtml" to "hi".toByteArray(), "b.bin" to byteArrayOf(1, 2))
        val out = readZipEntries(ByteArrayInputStream(zip), 1_000) { it.endsWith(".xhtml") }
        assertEquals(setOf("a.xhtml"), out.keys)
        assertArrayEquals("hi".toByteArray(), out["a.xhtml"])
        assertFalse(out.containsKey("b.bin"))
    }

    @Test fun throwsWhenTotalExceedsLimit() {
        // Highly compressible 2 MB entry (zip-bomb-like); limit is 1 KB.
        val big = ByteArray(2 * 1024 * 1024) // all zeros -> tiny compressed, huge inflated
        val zip = zipOf("big.html" to big)
        assertThrows(BookTooLargeException::class.java) {
            readZipEntries(ByteArrayInputStream(zip), 1024) { true }
        }
    }
}
