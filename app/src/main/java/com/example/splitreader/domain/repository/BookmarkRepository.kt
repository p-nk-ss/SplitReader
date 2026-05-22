package com.example.splitreader.domain.repository

import com.example.splitreader.data.local.BookmarkEntity
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeForBook(uri: String): Flow<List<BookmarkEntity>>
    suspend fun add(bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String? = null)
    suspend fun remove(bookUri: String, chapterIndex: Int, paragraphIndex: Int)
    suspend fun toggle(bookUri: String, chapterIndex: Int, paragraphIndex: Int)
}
