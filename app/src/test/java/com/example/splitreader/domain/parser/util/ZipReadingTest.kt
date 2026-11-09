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

    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()

    @Test fun spilledEntries_manyMediumImagesOverTheMemoryBudget_areStreamedToFiles() {
        // An illustrated book (Gutenberg's Count of Monte Cristo: 82 MiB of pictures, none over
        // 0.25 MiB) must not be held in memory whole: the pictures go to files, only text is kept.
        val images = (1..10).map { i -> "p$i.png" to ByteArray(400) { (it * i).toByte() } }
        val zip = zipOf("a.xhtml" to "text".toByteArray(), *images.toTypedArray())
        val out = readZipEntries(
            ByteArrayInputStream(zip), memoryBudget = 1000, totalBudget = 1_000_000,
            spillDir = tmp.newFolder("spill"), spill = { it.endsWith(".png") },
        ) { true }
        assertEquals(setOf("a.xhtml"), out.inMemory.keys)
        assertEquals(images.map { it.first }.toSet(), out.onDisk.keys)
        for ((name, bytes) in images) assertArrayEquals(bytes, out.onDisk.getValue(name).readBytes())
    }

    @Test fun inMemoryEntry_overTheMemoryBudget_throwsEvenWhenImagesAreSpilled() {
        val zip = zipOf("a.xhtml" to ByteArray(2000) { 1 })
        assertThrows(BookTooLargeException::class.java) {
            readZipEntries(
                ByteArrayInputStream(zip), memoryBudget = 1000, totalBudget = 1_000_000,
                spillDir = tmp.newFolder("spill"), spill = { it.endsWith(".png") },
            ) { true }
        }
    }

    @Test fun spilledEntries_overTheBookTotal_throw() {
        // The zip-bomb bound still covers what is written to disk.
        val zip = zipOf(*(1..10).map { "p$it.png" to ByteArray(400) }.toTypedArray())
        assertThrows(BookTooLargeException::class.java) {
            readZipEntries(
                ByteArrayInputStream(zip), memoryBudget = 1_000_000, totalBudget = 3000,
                spillDir = tmp.newFolder("spill"), spill = { it.endsWith(".png") },
            ) { true }
        }
    }

    @Test fun throwsWhenManyEntriesTogetherExceedLimit() {
        // Each entry alone is within the limit; combined they are not -- the budget is per book,
        // not per entry, so many medium entries can't add up to an OOM.
        val zip = zipOf(
            "a.xhtml" to ByteArray(400) { 1 },
            "b.xhtml" to ByteArray(400) { 2 },
            "c.xhtml" to ByteArray(400) { 3 },
        )
        assertThrows(BookTooLargeException::class.java) {
            readZipEntries(ByteArrayInputStream(zip), 1000) { true }
        }
    }
}
