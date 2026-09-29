package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/** Manages paragraph-level bookmarks within a book. */
interface BookmarkRepository {
    fun observeForBook(uri: String): Flow<List<Bookmark>>
    suspend fun add(bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String? = null)
    suspend fun remove(bookUri: String, chapterIndex: Int, paragraphIndex: Int)
    suspend fun toggle(bookUri: String, chapterIndex: Int, paragraphIndex: Int)
    suspend fun listForBook(bookUri: String): List<Bookmark>
    /** Atomically replaces every bookmark of [bookUri] with [bookmarks] (ids/bookUri ignored). */
    suspend fun replaceForBook(bookUri: String, bookmarks: List<Bookmark>)
}
