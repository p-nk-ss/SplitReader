package com.example.splitreader.domain.parser.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** DoS backstop for decompressed/whole-file reads. Generous — legitimate illustrated books pass. */
const val MAX_DECOMPRESSED: Long = 300L * 1024 * 1024

/**
 * [MAX_DECOMPRESSED] alone caps the *declared* budget, not what the device can actually hold: a
 * growing buffer (ByteArrayOutputStream, a StringBuilder) doubles its backing array as it nears
 * the cap, so the step just below the cap briefly needs the old array (~budget/2) plus the new one
 * (~budget) at once — about 1.5x the budget in contiguous heap. A ~192 MB-heap device (common on
 * tablets) OOMs well before a fixed 300 MB guard ever fires. Dividing the real heap down to a
 * fraction keeps that worst-case transient comfortably inside it on every device, while still
 * capping at [MAX_DECOMPRESSED] on large-heap devices. [maxMemory] defaults to the process's real
 * heap limit but is a parameter so tests can simulate a small one without touching the JVM's.
 */
fun effectiveMaxDecompressed(maxMemory: Long = Runtime.getRuntime().maxMemory()): Long =
    minOf(MAX_DECOMPRESSED, maxMemory / HEAP_BUDGET_DIVISOR)

private const val HEAP_BUDGET_DIVISOR: Long = 4

/** Thrown when a book's content exceeds the effective budget; surfaced to the user as a parse error. */
class BookTooLargeException(message: String) : Exception(message)

/**
 * Reads up to [n] bytes, looping until [n] is reached or EOF. InputStream.read(byte[]) may return
 * fewer bytes than requested even when more are available, so a single read is unreliable for
 * magic-byte detection. Returns exactly what was read (<= n).
 */
fun readUpTo(input: InputStream, n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = input.read(buf, off, n - off)
        if (r < 0) break
        off += r
    }
    return if (off == n) buf else buf.copyOf(off)
}

/** Reads the whole stream, throwing [BookTooLargeException] as soon as [maxBytes] is exceeded. */
fun readAllBounded(input: InputStream, maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val r = input.read(chunk)
        if (r < 0) break
        total += r
        if (total > maxBytes) throw BookTooLargeException("Book is too large to open safely.")
        out.write(chunk, 0, r)
    }
    return out.toByteArray()
}
