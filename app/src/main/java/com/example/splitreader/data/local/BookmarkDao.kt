package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookUri = :uri ORDER BY chapterIndex, paragraphIndex")
    fun observeForBook(uri: String): Flow<List<BookmarkEntity>>

    @Insert suspend fun insert(b: BookmarkEntity): Long
    @Delete suspend fun delete(b: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE bookUri = :uri AND chapterIndex = :ch AND paragraphIndex = :p LIMIT 1")
    suspend fun findAt(uri: String, ch: Int, p: Int): BookmarkEntity?

    @Query("DELETE FROM bookmarks WHERE bookUri = :uri AND chapterIndex = :ch AND paragraphIndex = :p")
    suspend fun deleteAt(uri: String, ch: Int, p: Int)

    @Query("SELECT * FROM bookmarks WHERE bookUri = :uri ORDER BY chapterIndex, paragraphIndex")
    suspend fun listForBook(uri: String): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE bookUri = :uri")
    suspend fun deleteForBook(uri: String)

    @Insert suspend fun insertAll(list: List<BookmarkEntity>)

    @Transaction
    suspend fun replaceForBook(uri: String, list: List<BookmarkEntity>) {
        deleteForBook(uri)
        insertAll(list)
    }
}
