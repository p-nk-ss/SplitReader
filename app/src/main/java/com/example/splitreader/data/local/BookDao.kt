package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    // Must update the existing row in place. Bookmarks/notes (CASCADE) and saved words/reading
    // sessions (SET NULL) reference books.uri, and INSERT OR REPLACE deletes the row first, which
    // fires those actions and wipes or orphans the reader's data on every re-import.
    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE uri = :uri")
    suspend fun updateLastOpenedAt(uri: String, timestamp: Long)

    @Query("DELETE FROM books WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uri = :uri)")
    suspend fun exists(uri: String): Boolean
}
