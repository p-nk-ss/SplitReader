package com.example.splitreader.domain.parser

import android.content.Context
import com.example.splitreader.domain.parser.util.StagedFiles
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
            val file = targetFile(context, bytes, baseName)
            file.writeBytes(bytes)
            file.absolutePath
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The file [save] would write [bytes] to (`filesDir/images/<sanitized baseName>.<sniffed ext>`),
     * without writing it; creates the directory. For callers that stage the bytes elsewhere first.
     */
    fun targetFile(context: Context, bytes: ByteArray, baseName: String): File {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val safe = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "img" }
        return File(dir, "$safe.${sniffExtension(bytes)}")
    }

    /**
     * A picture already on disk (an EPUB entry streamed out of the zip), copied -- never loaded whole --
     * into a temp file staged in [staged] for `filesDir/images/<sanitized baseName>.<sniffed ext>`.
     * Returns that final path (filled only when [staged] commits), or null if [source] is empty or
     * anything fails; the owned file at the final path is never touched here.
     */
    fun stage(context: Context, source: File, baseName: String, staged: StagedFiles): String? = try {
        if (source.length() == 0L) {
            null
        } else {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val safe = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "img" }
            val file = File(dir, "$safe.${sniffExtension(header(source))}")
            file.takeIf { f -> staged.stage(f) { source.copyTo(it) } }?.absolutePath
        }
    } catch (e: Exception) {
        staged.noteLost(e)
        null
    }

    /** The first bytes of [file] — enough for [sniffExtension] and an SVG/XML check. */
    fun header(file: File): ByteArray = file.inputStream().use { com.example.splitreader.domain.parser.util.readUpTo(it, 12) }
}
