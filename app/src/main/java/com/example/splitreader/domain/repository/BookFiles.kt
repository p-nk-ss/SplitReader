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

    /**
     * Deletes the covers and pictures in app storage that belong to none of [libraryUris] -- the
     * books the library shows. Such files are left by a parse that was still running when its book
     * was deleted (it then wrote the book's pictures anew) or whose book was never added (the free
     * limit); nothing else would ever remove them.
     *
     * A book whose files are not all older than an hour is never touched: it may be an import that
     * has not written its library row yet. Best-effort: never throws for a file that cannot be removed.
     */
    suspend fun sweepOrphans(libraryUris: Collection<String>)
}
