package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.SavedWord
import kotlinx.coroutines.flow.Flow

/** Stores and queries the user's saved vocabulary words. */
interface SavedWordRepository {
    fun observeAll(): Flow<List<SavedWord>>
    fun observeByLang(code: String): Flow<List<SavedWord>>
    fun search(q: String): Flow<List<SavedWord>>
    fun countByLang(code: String): Flow<Int>
    suspend fun findByWordAndLang(word: String, lang: String): SavedWord?
    suspend fun save(word: SavedWord): Long
    suspend fun update(word: SavedWord)
    suspend fun delete(word: SavedWord)
}
