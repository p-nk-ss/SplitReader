package com.example.splitreader.data.repository

import com.example.splitreader.data.local.BookDao
import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.repository.BookLibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookLibraryRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
) : BookLibraryRepository {

    override fun getAllBooks() = bookDao.getAllBooks()

    override suspend fun saveBook(book: Book) {
        bookDao.upsert(
            BookEntity(
                uri = book.filePath,
                title = book.title,
                author = book.author,
                coverPath = book.coverPath,
                lastOpenedAt = System.currentTimeMillis(),
                chapterCount = book.chapters.size,
            )
        )
    }

    override suspend fun deleteBook(uri: String) = bookDao.deleteByUri(uri)
}
