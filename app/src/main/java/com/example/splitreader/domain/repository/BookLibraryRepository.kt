package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.model.StoredBook
import kotlinx.coroutines.flow.Flow

/** Stores the user's library of opened books and their last-opened ordering. */
interface BookLibraryRepository {
    fun getAllBooks(): Flow<List<LibraryBook>>
    suspend fun saveBook(book: Book)
    suspend fun touchBook(uri: String)

    /**
     * Takes the book out of the library but keeps what the reader made in it: from now on it is
     * absent from [getAllBooks], [bookCount] and [exists], while its bookmarks and notes stay stored
     * and come back when the same book is saved again.
     */
    suspend fun removeBook(uri: String)

    /** Deletes the book for good, with its bookmarks and notes -- also one that was only removed. */
    suspend fun deleteBook(uri: String)

    /** Current number of books in the library — used to enforce the free-tier limit. */
    suspend fun bookCount(): Int

    /** Whether a book with [uri] is already in the library (i.e. adding it is a re-open, not new). */
    suspend fun exists(uri: String): Boolean

    // ── The Same book (see SameBook): the only reads that also see Removed books ──────────────

    /** The row for [uri], in the library or removed; null when the library never held it. */
    suspend fun stored(uri: String): StoredBook?

    /** A row, in the library or removed, whose content key is [contentKey]; one in the library first. */
    suspend fun withContent(contentKey: String): StoredBook?

    suspend fun setContentKey(uri: String, contentKey: String)

    /**
     * Moves the row of [from] -- and its bookmarks, notes, saved words and reading sessions -- to
     * [to], in one transaction. [to] must have no row. The moved row has no cover: its file was
     * named after [from].
     */
    suspend fun moveBook(from: String, to: String)
}
