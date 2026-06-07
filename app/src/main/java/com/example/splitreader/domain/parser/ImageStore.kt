package com.example.splitreader.domain.parser

import android.content.Context
import java.io.File

/**
 * Saves extracted inline-illustration bytes to `filesDir/images` so the reader can display them by
 * path (same scheme covers use). Format is sniffed from magic bytes; all failures are swallowed so a
 * bad image can never break a parse.
 */
object ImageStore {

    /** Sniffs an image extension from magic bytes (jpg/png/gif/webp); defaults to jpg. */
    fun sniffExtension(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0].toInt() and 0xFF == 0xFF && bytes[1].toInt() and 0xFF == 0xD8 -> "jpg"
        bytes.size >= 4 && bytes[0].toInt() and 0xFF == 0x89 && bytes[1].toInt().toChar() == 'P' -> "png"
        bytes.size >= 3 && bytes[0].toInt().toChar() == 'G' && bytes[1].toInt().toChar() == 'I' -> "gif"
        bytes.size >= 12 && bytes[0].toInt().toChar() == 'R' && bytes[1].toInt().toChar() == 'I' &&
            bytes[8].toInt().toChar() == 'W' && bytes[9].toInt().toChar() == 'E' -> "webp"
        else -> "jpg"
    }

    /**
     * Writes [bytes] to `filesDir/images/<sanitized baseName>.<sniffed ext>` and returns the absolute
     * path, or null if [bytes] is empty or anything throws. [baseName] is sanitized to a legal filename.
     */
    fun save(context: Context, bytes: ByteArray, baseName: String): String? = try {
        if (bytes.isEmpty()) {
            null
        } else {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val safe = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "img" }
            val file = File(dir, "$safe.${sniffExtension(bytes)}")
            file.writeBytes(bytes)
            file.absolutePath
        }
    } catch (_: Exception) {
        null
    }
}
