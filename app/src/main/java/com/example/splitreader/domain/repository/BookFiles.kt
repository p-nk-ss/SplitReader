package com.example.splitreader.domain.repository

/**
 * The files the app keeps for a library book in its own storage: the cover, the book's pictures and,
 * for a catalog or Drive book, the downloaded book file itself.
 */
interface BookFiles {
    /**
     * Deletes everything the app stored for [bookUri]: its cover, its pictures (and their unfinished
     * temp files) and the downloaded book file when it lives in app-private storage. A file the
     * reader picked from their own storage is never deleted -- only the app's persisted read grant
     * for it is released. Best-effort: never throws for a file that cannot be removed.
     */
    suspend fun deleteFor(bookUri: String)
}
