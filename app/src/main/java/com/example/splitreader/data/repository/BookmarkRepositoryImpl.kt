package com.example.splitreader.data.repository

import com.example.splitreader.data.local.BookmarkDao
import com.example.splitreader.data.local.BookmarkEntity
import com.example.splitreader.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {
    override fun observeForBook(uri: String): Flow<List<BookmarkEntity>> = dao.observeForBook(uri)

    override suspend fun add(bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String?) {
        dao.insert(BookmarkEntity(bookUri = bookUri, chapterIndex = chapterIndex, paragraphIndex = paragraphIndex, label = label))
    }

    override suspend fun remove(bookUri: String, chapterIndex: Int, paragraphIndex: Int) {
        dao.deleteAt(bookUri, chapterIndex, paragraphIndex)
    }

    override suspend fun toggle(bookUri: String, chapterIndex: Int, paragraphIndex: Int) {
        if (dao.findAt(bookUri, chapterIndex, paragraphIndex) != null) {
            dao.deleteAt(bookUri, chapterIndex, paragraphIndex)
        } else {
            dao.insert(BookmarkEntity(bookUri = bookUri, chapterIndex = chapterIndex, paragraphIndex = paragraphIndex))
        }
    }
}
