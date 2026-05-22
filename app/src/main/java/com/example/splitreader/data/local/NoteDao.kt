package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookUri = :uri ORDER BY chapterIndex, paragraphIndex")
    fun observeForBook(uri: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(n: NoteEntity)

    @Delete suspend fun delete(n: NoteEntity)
}
