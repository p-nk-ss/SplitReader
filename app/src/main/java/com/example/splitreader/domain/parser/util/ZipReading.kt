package com.example.splitreader.domain.parser.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads every non-directory zip entry whose name satisfies [accept] into a name -> bytes map,
 * accumulating the total decompressed size and throwing [BookTooLargeException] the moment it
 * exceeds [maxTotal]. This bounds a maliciously compressed (zip-bomb) EPUB before it exhausts memory.
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
