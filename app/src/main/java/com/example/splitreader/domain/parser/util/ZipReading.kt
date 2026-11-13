package com.example.splitreader.domain.parser.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Reads every non-directory zip entry whose name satisfies [accept] into a name -> bytes map,
 * accumulating the total decompressed size *across all entries* and throwing [BookTooLargeException]
 * the moment it exceeds [maxTotal]. This bounds a maliciously compressed (zip-bomb) EPUB, and also
 * many medium-sized entries that would otherwise add up to an OOM, before it exhausts memory.
 */
fun readZipEntries(
    input: InputStream,
    maxTotal: Long,
    accept: (String) -> Boolean,
): Map<String, ByteArray> = readZipEntries(input, maxTotal, maxTotal, null, { false }, accept = accept).inMemory

/** What [readZipEntries] read: entries held in memory, and entries streamed to files. */
class ZipContents(val inMemory: Map<String, ByteArray>, val onDisk: Map<String, File>)

/**
 * Like the in-memory [readZipEntries], but entries matching [spill] (an EPUB's pictures) are
 * streamed to files in [spillDir] instead of being held: an illustrated book is mostly pictures
 * (Gutenberg's Count of Monte Cristo: 82.4 MiB of entries, none over 0.25 MiB), and holding them
 * all at once is what a heap-scaled budget would have to refuse. Two bounds, both running totals
 * checked as bytes stream in, before anything more is stored:
 *  - [memoryBudget]: the entries held in memory (text), summed — a heap fraction, see
 *    [effectiveMaxDecompressed]; it bounds each in-memory entry too;
 *  - [totalBudget]: every accepted entry, held or spilled — the zip-bomb bound on disk use.
 *
 * [beforeChunk] runs before every entry and every chunk read (8 KiB): the caller's cancellation
 * check. Whatever it throws ends the read; a big book is hundreds of MB of entries.
 */
fun readZipEntries(
    input: InputStream,
    memoryBudget: Long,
    totalBudget: Long,
    spillDir: File?,
    spill: (String) -> Boolean,
    beforeChunk: () -> Unit = {},
    accept: (String) -> Boolean,
): ZipContents {
    val inMemory = LinkedHashMap<String, ByteArray>()
    val onDisk = LinkedHashMap<String, File>()
    var inMemoryTotal = 0L
    var total = 0L
    // A running count of entries spilled so far -- NOT onDisk.size, which is the number of
    // distinct *names* and can stall when a later entry repeats an earlier one's name (two
    // entries can legally share a name in a zip; the later one wins, as the in-memory map
    // already does). Naming files by onDisk.size would then reuse a path a still-live map
    // entry points to, and a third, differently-named entry would overwrite it on disk.
    var spilledCount = 0
    val chunk = ByteArray(8 * 1024)
    fun tooLarge(): Nothing = throw BookTooLargeException("Book is too large to open safely.")
    spillDir?.mkdirs()
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            beforeChunk()
            if (!entry.isDirectory && accept(entry.name)) {
                val toDisk = spillDir != null && spill(entry.name)
                // Cheap pre-check: an entry whose own declared size already busts the remaining
                // budget is rejected before allocating anything for it. The size field can still
                // under-report (that's how some zip bombs work), so this is an optimization, not
                // the authoritative check — the streamed running totals below are.
                val declaredSize = entry.size
                if (declaredSize >= 0) {
                    if (total + declaredSize > totalBudget) tooLarge()
                    if (!toDisk && inMemoryTotal + declaredSize > memoryBudget) tooLarge()
                }
                fun copy(out: OutputStream) {
                    while (true) {
                        beforeChunk()
                        val r = zip.read(chunk)
                        if (r < 0) break
                        total += r
                        if (total > totalBudget) tooLarge()
                        if (!toDisk) {
                            inMemoryTotal += r
                            if (inMemoryTotal > memoryBudget) tooLarge()
                        }
                        out.write(chunk, 0, r)
                    }
                }
                if (toDisk) {
                    // Named by a running counter, never by the entry name (a zip name may hold
                    // "../") nor by onDisk.size (see spilledCount above).
                    val file = File(spillDir, "e${spilledCount++}")
                    file.outputStream().buffered().use { copy(it) }
                    onDisk[entry.name] = file
                } else {
                    val out = ByteArrayOutputStream()
                    copy(out)
                    inMemory[entry.name] = out.toByteArray()
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return ZipContents(inMemory, onDisk)
}
