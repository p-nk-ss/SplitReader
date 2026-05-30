package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWordDao {
    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedWordEntity>>

    @Query("SELECT * FROM saved_words WHERE sourceLang = :lang ORDER BY savedAt DESC")
    fun observeByLang(lang: String): Flow<List<SavedWordEntity>>

    @Query("SELECT * FROM saved_words WHERE word LIKE :q || '%' OR translation LIKE :q || '%' ORDER BY savedAt DESC")
    fun search(q: String): Flow<List<SavedWordEntity>>

    @Query("SELECT COUNT(*) FROM saved_words WHERE sourceLang = :lang")
    fun countByLang(lang: String): Flow<Int>

    @Query("SELECT * FROM saved_words WHERE word = :word AND sourceLang = :lang LIMIT 1")
    suspend fun findByWordAndLang(word: String, lang: String): SavedWordEntity?

    @Insert suspend fun insert(w: SavedWordEntity): Long
    @Update suspend fun update(w: SavedWordEntity)
    @Delete suspend fun delete(w: SavedWordEntity)
}
