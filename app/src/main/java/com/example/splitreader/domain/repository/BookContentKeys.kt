package com.example.splitreader.domain.repository

/** Fingerprints a book file by its content, so the same file under another uri is the same book. */
interface BookContentKeys {
    /**
     * The SHA-256 (hex) of the whole file at [uri], streamed off the main thread; null when the file
     * cannot be read. Reads the entire file: call it only when the answer is needed.
     */
    suspend fun keyOf(uri: String): String?
}
