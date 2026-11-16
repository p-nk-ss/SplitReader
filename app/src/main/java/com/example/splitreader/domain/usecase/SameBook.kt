package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.repository.BookContentKeys
import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Recognises the Same book (CONTEXT.md) when a book is added under a uri the library has never held,
 * and makes that uri the book's: its row, bookmarks, notes, saved words, reading sessions and
 * everything [ReadingPreferences] keeps for it move there.
 *
 * Only the add path asks ([AddBookToLibraryUseCase]): everywhere else a book is its uri. The file is
 * read whole (SHA-256 of its content) only for a uri the library does not know, or once for a known
 * book that has no key yet (added before v29) -- re-opening a known book costs no extra read.
 *
 * Known limit: a book removed before v29 has no key and cannot be matched. Its reading data waits
 * under its old uri as before.
 */
class SameBook @Inject constructor(
    private val library: BookLibraryRepository,
    private val keys: BookContentKeys,
    private val preferences: ReadingPreferences,
    private val files: BookFiles,
) {
    /** What the library already holds of the book about to be added. */
    sealed interface Found {
        /** Whether the book is in the library already -- adding it then needs no free slot. */
        val inLibrary: Boolean
    }

    /** The library has held this very uri (in it, or removed): nothing to recognise. */
    private data class ThisUri(override val inLibrary: Boolean, val hasKey: Boolean) : Found

    /** The same file is the library's book under [uri] (in it, or removed). */
    private data class OtherUri(val uri: String, override val inLibrary: Boolean, val key: String) : Found

    /** A book the library has never held; [key] is null when the file could not be read. */
    private data class NewBook(val key: String?) : Found {
        override val inLibrary get() = false
    }

    /** Looks [uri] up -- by uri, then (a uri new to the library) by the file's content. */
    suspend fun find(uri: String): Found {
        library.stored(uri)?.let { return ThisUri(inLibrary = !it.removed, hasKey = it.contentKey != null) }
        val key = keys.keyOf(uri) ?: return NewBook(key = null)
        val same = library.withContent(key) ?: return NewBook(key)
        return OtherUri(same.uri, inLibrary = !same.removed, key = key)
    }

    /**
     * Saves [book] as [found] said. For the Same book under another uri, its data moves to
     * [Book.filePath] first and the old uri's files (cover, pictures, read grant) are deleted. The
     * move and the save run to the end even if the caller is cancelled: half of a move would leave
     * the place under one uri and the book under the other.
     */
    suspend fun save(book: Book, found: Found) {
        val uri = book.filePath
        when (found) {
            is ThisUri -> {
                library.saveBook(book)
                if (!found.hasKey) rememberContent(uri)
            }
            is OtherUri -> withContext(NonCancellable) {
                library.moveBook(found.uri, uri)
                preferences.moveBook(found.uri, uri)
                library.saveBook(book)
                try {
                    files.deleteFor(found.uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Best-effort: what is left behind is an orphan the start-up sweep removes.
                }
            }
            is NewBook -> withContext(NonCancellable) {
                library.saveBook(book)
                found.key?.let { library.setContentKey(uri, it) }
            }
        }
    }

    /**
     * Gives a known book without a key (one added before v29) its key, so it can be recognised
     * later. Call when the book is opened. Reads the file only then; a file that cannot be read
     * leaves the book as it was.
     */
    suspend fun rememberContent(uri: String) {
        try {
            val stored = library.stored(uri) ?: return
            if (stored.contentKey != null) return
            keys.keyOf(uri)?.let { library.setContentKey(uri, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: the book opens either way and tries again on its next open.
        }
    }
}
