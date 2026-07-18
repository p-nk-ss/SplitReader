package com.example.splitreader.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.parser.BookParser
import com.example.splitreader.domain.parser.util.readUpTo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Resolves the book format by asking each registered [BookParser] whether it [BookParser.canParse]
 * the file (name, MIME type, and a small header peek), then delegates to the first match. Adding a
 * format requires no change here — just a new parser in the registry. Emits [ParseResult].
 */
class ParseBookUseCase @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards BookParser>,
    @ApplicationContext private val context: Context,
    private val crashReporter: CrashReporter,
) {
    /** Parses the book at [uri], emitting loading → success/error. Runs on [Dispatchers.IO]. */
    operator fun invoke(uri: Uri): Flow<ParseResult> = flow {
        emit(ParseResult.Loading)
        try {
            val fileName = getFileName(uri) ?: uri.lastPathSegment ?: ""
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val header = readHeaderBytes(uri, 256)

            Log.d("PARSER", "File: $fileName, MIME: $mimeType, parsers: ${parsers.size}")

            val parser = parsers.firstOrNull { it.canParse(fileName, mimeType, header) }
                ?: throw IllegalArgumentException(
                    "Unsupported format: $fileName\nSupported: " +
                        parsers.flatMap { it.supportedExtensions }
                            .distinct()
                            .joinToString(", ") { ".$it" }
                )

            val book = parser.parse(uri, context)
            emit(ParseResult.Success(book))
        } catch (e: Exception) {
            Log.e("PARSER", "Parse error: ${e.message}", e)
            crashReporter.recordNonFatal(e, "Book parse failed")
            emit(ParseResult.Error(e.message ?: "Failed to open book"))
        }
    }.flowOn(Dispatchers.IO)

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    /** Reads the first [byteCount] bytes (or fewer) of the file for magic-byte format detection. */
    private fun readHeaderBytes(uri: Uri, byteCount: Int): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            readUpTo(stream, byteCount)
        } ?: ByteArray(0)
}
