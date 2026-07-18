package com.example.splitreader.data.repository

import com.example.splitreader.data.local.SavedWordDao
import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.data.repository.mapper.toDomain
import com.example.splitreader.data.repository.mapper.toEntity
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.domain.repository.SavedWordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedWordRepositoryImpl @Inject constructor(
    private val dao: SavedWordDao,
) : SavedWordRepository {
    override fun observeAll(): Flow<List<SavedWord>> = dao.observeAll().map { it.map(SavedWordEntity::toDomain) }
    override fun observeByLang(code: String): Flow<List<SavedWord>> = dao.observeByLang(code).map { it.map(SavedWordEntity::toDomain) }
    override fun search(q: String): Flow<List<SavedWord>> = dao.search(q).map { it.map(SavedWordEntity::toDomain) }
    override fun countByLang(code: String): Flow<Int> = dao.countByLang(code)
    override suspend fun findByWordAndLang(word: String, lang: String): SavedWord? =
        dao.findByWordAndLang(word, lang)?.toDomain()
    override suspend fun save(word: SavedWord): Long = dao.insert(word.toEntity())
    override suspend fun update(word: SavedWord) = dao.update(word.toEntity())
    override suspend fun delete(word: SavedWord) = dao.delete(word.toEntity())
}
