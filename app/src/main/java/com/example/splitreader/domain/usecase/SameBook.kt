package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.IoDispatcher
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.repository.BookContentKeys
import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognises the Same book (CONTEXT.md) when a book is added under a uri the library has never held,
 * and makes that uri the book's: its row, bookmarks, notes, saved words, reading sessions and
 * everything [ReadingPreferences] keeps for it move there.
 *
 * Only the add path asks ([AddBookToLibraryUseCase]): everywhere else a book is its uri. The file is
 * read whole (SHA-256 of its content) only for a uri the library does not know, or once for a known
 * book that has no key yet (added before v29) -- re-opening a known book costs no extra read.
 *
 * A match is checked before it is trusted: the old uri's file is read again when it can be, and a
 * file that now holds another book only gets its key renewed (a removed book whose file is gone is
 * trusted by its stored key).
 *
 * Known limit: a book removed before v29 has no key and cannot be matched. Its reading data waits
 * under its old uri as before.
 */
@Singleton
class SameBook @Inject constructor(
    private val library: BookLibraryRepository,
    private val keys: BookContentKeys,
    private val preferences: ReadingPreferences,
    private val files: BookFiles,
    @IoDispatcher private val io: CoroutineDispatcher,
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

    private val mutex = Mutex()

    /**
     * Runs [block] -- one add's [find], free-slot decision and [save] -- alone. Two adds of one file
     * at once would otherwise both find the book under its old uri: both skip the free-slot check,
     * the second moves nothing (the row has gone) and saves a card of its own.
     */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    /** Looks [uri] up -- by uri, then (a uri new to the library) by the file's content. */
    suspend fun find(uri: String): Found {
        library.stored(uri)?.let { return ThisUri(inLibrary = !it.removed, hasKey = it.contentKey != null) }
        val key = keys.keyOf(uri) ?: return NewBook(key = null)
        while (true) {
            val same = library.withContent(key) ?: return NewBook(key)
            // A key is taken when the book is added: the document under that uri may since hold
            // another book (an overwritten download, a Drive file replaced). Unreadable: trust it.
            val now = keys.keyOf(same.uri)
            if (now == null || now == key) return OtherUri(same.uri, inLibrary = !same.removed, key = key)
            library.setContentKey(same.uri, now) // no longer this book; look again
        }
    }

    /**
     * Whether the book at [uri] is in the library -- under [uri] or, read by content, under another
     * uri. Reads the file when [uri] is new to the library: ask only when the answer matters.
     */
    suspend fun isInLibrary(uri: String): Boolean = find(uri).inLibrary

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
                // Copy, move, forget: a process death between any two steps leaves the place under
                // the uri that has the row (a stray copy under the other is harmless).
                // The prefs' writes are synchronous commits: off the caller's (main) thread.
                withContext(io) { preferences.copyBook(found.uri, uri) }
                library.moveBook(found.uri, uri)
                withContext(io) { preferences.forgetBook(found.uri) }
                library.saveBook(book)
                if (sameFile(found.uri, uri)) return@withContext // its "old" files are the book's own
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

    /** Whether two file:// uris name one file under two spellings (`/data/user/0/…` and `/data/data/…`). */
    private fun sameFile(a: String, b: String): Boolean = try {
        val fa = fileOf(a)
        val fb = fileOf(b)
        fa != null && fb != null && fa.canonicalPath == fb.canonicalPath
    } catch (_: Exception) {
        false
    }

    private fun fileOf(uri: String): File? =
        URI(uri).takeIf { it.scheme == "file" }?.path?.let(::File)
}
