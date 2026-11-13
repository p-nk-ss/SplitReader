package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The single way a book leaves the library. Either way the book is gone from every library list,
 * stops counting toward the free-tier limit (adding it again needs a free slot, like any new book)
 * and its files in app storage are removed: cover, pictures, a catalog/Drive download (see
 * [BookFiles]) -- they are recreated when the book is opened again.
 *
 * What happens to the reader's own work in the book is their choice ([delete]'s `alsoReadingData`):
 *  - kept (the dialog's default): Reading position, progress, excerpt, "finished", the source
 *    language they chose, bookmarks and notes stay stored, out of sight. The same book added again
 *    (same uri: a catalog or Drive book always, a picked file while it is not moved) opens where
 *    they stopped, with its bookmarks and notes.
 *  - deleted: the library row goes for good -- with it the bookmarks and notes -- and everything
 *    remembered about the book is forgotten, so the same file added again is a new, unread book.
 *
 * Never deleted: saved words and reading sessions (the reader's vocabulary and the Almanac's
 * history outlive the book) and cached translations (keyed by text, shared between books).
 *
 * Kept data is deleted only by deleting the book with `alsoReadingData` -- which means adding it
 * again first; nothing else sweeps it. It is small (one row, its bookmarks and notes, a few
 * preference keys) and the reader was told it is kept.
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
    suspend fun delete(uri: String, alsoReadingData: Boolean) = withContext(NonCancellable) {
        if (alsoReadingData) {
            library.deleteBook(uri)
            preferences.forgetBook(uri)
        } else {
            library.removeBook(uri)
        }
        try {
            files.deleteFor(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: the book is gone from the library either way.
        }
    }
}
