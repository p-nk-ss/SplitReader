package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/** Manages paragraph-level bookmarks within a book. */
interface BookmarkRepository {
    fun observeForBook(uri: String): Flow<List<Bookmark>>
    suspend fun add(bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String? = null)
    suspend fun remove(bookUri: String, chapterIndex: Int, paragraphIndex: Int)
    suspend fun toggle(bookUri: String, chapterIndex: Int, paragraphIndex: Int)
}
