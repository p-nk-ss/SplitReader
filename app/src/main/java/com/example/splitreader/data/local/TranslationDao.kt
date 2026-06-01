package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationDao {

    @Query("SELECT * FROM translation_cache WHERE id = :id")
    suspend fun getCached(id: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache WHERE timestamp < :before")
    suspend fun clearOlderThan(before: Long)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun count(): Int
}
