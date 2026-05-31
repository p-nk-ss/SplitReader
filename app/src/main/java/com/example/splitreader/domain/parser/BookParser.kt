package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book

/**
 * A parser for a single e-book format. Implementations are collected into a registry
 * (a `Set<BookParser>` via Hilt multibinding) and selected by [canParse]; adding a new
 * format is a new implementation plus one binding, with no dispatcher changes.
 */
interface BookParser {

    /** File extensions this parser claims, without the dot — e.g. `["epub"]`, `["fb2", "fb2.xml"]`. */
    val supportedExtensions: List<String>

    /**
     * Cheap pre-parse check used by the registry to route a file to this parser. The default
     * matches on [supportedExtensions]; override to also sniff MIME type or magic bytes.
     *
     * @param header the first bytes of the file (typically ~128) for magic-byte detection.
     */
    fun canParse(fileName: String, mimeType: String, header: ByteArray): Boolean =
        supportedExtensions.any { fileName.endsWith(".$it", ignoreCase = true) }

    suspend fun parse(uri: Uri, context: Context): Book
}
