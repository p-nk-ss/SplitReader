package com.example.splitreader.domain.repository

import com.example.splitreader.data.local.SavedWordEntity
import kotlinx.coroutines.flow.Flow

interface SavedWordRepository {
    fun observeAll(): Flow<List<SavedWordEntity>>
    fun observeByLang(code: String): Flow<List<SavedWordEntity>>
    fun search(q: String): Flow<List<SavedWordEntity>>
    fun countByLang(code: String): Flow<Int>
    suspend fun save(word: SavedWordEntity): Long
    suspend fun update(word: SavedWordEntity)
    suspend fun delete(word: SavedWordEntity)
}
