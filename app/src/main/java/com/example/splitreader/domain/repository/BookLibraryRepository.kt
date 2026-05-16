package com.example.splitreader.domain.repository

import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookLibraryRepository {
    fun getAllBooks(): Flow<List<BookEntity>>
    suspend fun saveBook(book: Book)
    suspend fun deleteBook(uri: String)
}
