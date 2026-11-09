package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
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

/**
 * A minimal raw (STORED-method) zip writer that, unlike [ZipOutputStream], allows two entries with
 * the same name -- real-world malformed/duplicate-name zips exist even though Java's own writer
 * refuses to produce one. [ZipReading] reads entries sequentially off local file headers (as
 * [java.util.zip.ZipInputStream] does), so only those headers + data need to be well-formed; the
 * central directory is included too, for a fully valid archive.
 */
private fun rawZipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    fun ByteArrayOutputStream.short(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
    fun ByteArrayOutputStream.int(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }
    fun crc32(data: ByteArray): Int = CRC32().apply { update(data) }.value.toInt()
    val out = ByteArrayOutputStream()
    val offsets = mutableListOf<Int>()
    for ((name, data) in entries) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        offsets.add(out.size())
        out.int(0x04034b50)           // local file header signature
        out.short(20); out.short(0); out.short(0); out.short(0); out.short(0) // version, flags, method(stored), time, date
        out.int(crc32(data))
        out.int(data.size); out.int(data.size)
        out.short(nameBytes.size); out.short(0)
        out.write(nameBytes)
        out.write(data)
    }
    val cdStart = out.size()
    entries.forEachIndexed { i, (name, data) ->
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        out.int(0x02014b50)           // central directory header signature
        out.short(20); out.short(20); out.short(0); out.short(0); out.short(0); out.short(0)
        out.int(crc32(data))
        out.int(data.size); out.int(data.size)
        out.short(nameBytes.size); out.short(0); out.short(0); out.short(0); out.short(0)
        out.int(0)
        out.int(offsets[i])
        out.write(nameBytes)
    }
    val cdEnd = out.size()
    out.int(0x06054b50)               // end of central directory signature
    out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
    out.int(cdEnd - cdStart); out.int(cdStart); out.short(0)
    return out.toByteArray()
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

    @Test fun spilledEntries_duplicateNames_doNotCollideOnDisk() {
        // Two entries share a name (the later one wins, as the in-memory map already does for
        // text); a third, genuinely new entry must still get its own temp file -- not reuse one
        // whose path is still the value a duplicate-named entry points to in the map.
        val first = ByteArray(10) { 1 }
        val second = ByteArray(10) { 2 }  // "dup.png" second occurrence -- this is what should remain
        val third = ByteArray(10) { 3 }
        val zip = rawZipOf("dup.png" to first, "dup.png" to second, "third.png" to third)
        val out = readZipEntries(
            ByteArrayInputStream(zip), memoryBudget = 1_000_000, totalBudget = 1_000_000,
            spillDir = tmp.newFolder("spill"), spill = { it.endsWith(".png") },
        ) { true }
        assertArrayEquals(second, out.onDisk.getValue("dup.png").readBytes())
        assertArrayEquals(third, out.onDisk.getValue("third.png").readBytes())
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
