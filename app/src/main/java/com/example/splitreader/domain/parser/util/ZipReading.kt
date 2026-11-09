package com.example.splitreader.domain.parser.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
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
): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    var total = 0L
    val chunk = ByteArray(8 * 1024)
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && accept(entry.name)) {
                // Cheap pre-check: an entry whose own declared size already busts the remaining
                // budget is rejected before allocating anything for it. The size field can still
                // under-report (that's how some zip bombs work), so this is an optimization, not
                // the authoritative check — the streamed running total below is.
                val declaredSize = entry.size
                if (declaredSize >= 0 && total + declaredSize > maxTotal) {
                    throw BookTooLargeException("Book is too large to open safely.")
                }
                val out = ByteArrayOutputStream()
                while (true) {
                    val r = zip.read(chunk)
                    if (r < 0) break
                    total += r
                    if (total > maxTotal) throw BookTooLargeException("Book is too large to open safely.")
                    out.write(chunk, 0, r)
                }
                result[entry.name] = out.toByteArray()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}
