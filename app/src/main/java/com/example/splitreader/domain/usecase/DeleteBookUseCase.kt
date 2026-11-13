package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The single way a book leaves the library. Removes, in this order:
 *  1. the library row -- with it the book's bookmarks and notes (they are positions in this book);
 *  2. everything remembered about the book: Reading position, progress, excerpt, "finished", the
 *     source language the reader chose -- so the same file imported again is a new, unread book;
 *  3. its files in app storage: cover, pictures, a catalog/Drive download (see [BookFiles]).
 *
 * Kept on purpose: saved words and reading sessions (the reader's vocabulary and the Almanac's
 * history outlive the book; they only lose their link to it) and cached translations (keyed by
 * text, shared between books).
 *
 * The row goes first: if that fails nothing else is touched and the book stays whole. Once it is
 * gone the rest runs to the end even if the caller is cancelled -- a half-deleted book would leave
 * exactly the stale progress and orphan files this use case exists to remove.
 */
class DeleteBookUseCase @Inject constructor(
    private val library: BookLibraryRepository,
    private val preferences: ReadingPreferences,
    private val files: BookFiles,
) {
    suspend fun delete(uri: String) = withContext(NonCancellable) {
        library.deleteBook(uri)
        preferences.forgetBook(uri)
        try {
            files.deleteFor(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: the book is gone from the library either way.
        }
    }
}
