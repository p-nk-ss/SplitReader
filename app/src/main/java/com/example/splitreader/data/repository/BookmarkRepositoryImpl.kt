package com.example.splitreader.data.repository

import com.example.splitreader.data.local.BookmarkDao
import com.example.splitreader.data.local.BookmarkEntity
import com.example.splitreader.data.repository.mapper.toDomain
import com.example.splitreader.data.repository.mapper.toEntity
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {
    override fun observeForBook(uri: String): Flow<List<Bookmark>> =
        dao.observeForBook(uri).map { list -> list.map { it.toDomain() } }

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

    override suspend fun listForBook(bookUri: String): List<Bookmark> =
        dao.listForBook(bookUri).map { it.toDomain() }

    override suspend fun replaceForBook(bookUri: String, bookmarks: List<Bookmark>) {
        dao.replaceForBook(bookUri, bookmarks.map { it.toEntity().copy(id = 0, bookUri = bookUri) })
    }
}
