package com.example.splitreader.domain.repository

import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.domain.model.Book
import kotlinx.coroutines.flow.Flow

/** Stores the user's library of opened books and their last-opened ordering. */
interface BookLibraryRepository {
    fun getAllBooks(): Flow<List<BookEntity>>
    suspend fun saveBook(book: Book)
    suspend fun touchBook(uri: String)
    suspend fun deleteBook(uri: String)
}
