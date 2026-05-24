package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE uri = :uri")
    suspend fun updateLastOpenedAt(uri: String, timestamp: Long)

    @Query("DELETE FROM books WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}
