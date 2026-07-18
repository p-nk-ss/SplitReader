package com.example.splitreader.data.repository

import com.example.splitreader.data.local.BookDao
import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.data.repository.mapper.toDomain
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.repository.BookLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookLibraryRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
) : BookLibraryRepository {

    override fun getAllBooks(): Flow<List<LibraryBook>> =
        bookDao.getAllBooks().map { list -> list.map { it.toDomain() } }

    override suspend fun saveBook(book: Book) {
        bookDao.upsert(
            BookEntity(
                uri = book.filePath,
                title = book.title,
                author = book.author,
                coverPath = book.coverPath,
                lastOpenedAt = System.currentTimeMillis(),
                chapterCount = book.chapters.size,
                synopsis = book.synopsis,
            )
        )
    }

    override suspend fun touchBook(uri: String) =
        bookDao.updateLastOpenedAt(uri, System.currentTimeMillis())

    override suspend fun deleteBook(uri: String) = bookDao.deleteByUri(uri)

    override suspend fun bookCount(): Int = bookDao.count()

    override suspend fun exists(uri: String): Boolean = bookDao.exists(uri)
}
