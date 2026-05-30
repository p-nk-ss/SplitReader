package com.example.splitreader.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.parser.EpubParser
import com.example.splitreader.domain.parser.Fb2Parser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Resolves the book format from the file name, MIME type, or content sniffing, then delegates
 * to the matching [EpubParser]/[Fb2Parser]. Emits [ParseResult] (loading, success, or error).
 */
class ParseBookUseCase @Inject constructor(
    private val fb2Parser: Fb2Parser,
    private val epubParser: EpubParser,
    @ApplicationContext private val context: Context,
) {
    /** Parses the book at [uri], emitting loading → success/error. Runs on [Dispatchers.IO]. */
    operator fun invoke(uri: Uri): Flow<ParseResult> = flow {
        emit(ParseResult.Loading)
        try {
            val fileName = getFileName(uri) ?: uri.lastPathSegment ?: ""
            val mimeType = context.contentResolver.getType(uri) ?: ""

            Log.d("PARSER", "File: $fileName, MIME: $mimeType")

            val isFb2 = fileName.endsWith(".fb2", ignoreCase = true) ||
                fileName.endsWith(".fb2.xml", ignoreCase = true) ||
                mimeType.contains("fb2") ||
                mimeType == "text/xml" ||
                mimeType == "application/xml"

            val isEpub = fileName.endsWith(".epub", ignoreCase = true) ||
                mimeType.contains("epub")

            val parser = when {
                isEpub -> epubParser
                isFb2 -> fb2Parser
                else -> {
                    val preview = readFirstBytes(uri, 500)
                    when {
                        preview.contains("<FictionBook", ignoreCase = true) -> fb2Parser
                        preview.contains("epub", ignoreCase = true) -> epubParser
                        else -> throw IllegalArgumentException(
                            "Unsupported format: $fileName\nSupported: .fb2, .fb2.xml, .epub"
                        )
                    }
                }
            }

            val book = parser.parse(uri, context)
            emit(ParseResult.Success(book))
        } catch (e: Exception) {
            Log.e("PARSER", "Parse error: ${e.message}", e)
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

    private fun readFirstBytes(uri: Uri, byteCount: Int): String =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = ByteArray(byteCount)
            val read = stream.read(bytes)
            String(bytes, 0, read, Charsets.UTF_8)
        } ?: ""
}
