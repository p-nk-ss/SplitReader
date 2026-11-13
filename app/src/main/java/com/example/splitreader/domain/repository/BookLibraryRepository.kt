package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
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
}
