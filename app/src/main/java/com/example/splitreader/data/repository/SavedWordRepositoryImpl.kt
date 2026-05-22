package com.example.splitreader.data.repository

import com.example.splitreader.data.local.SavedWordDao
import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.repository.SavedWordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedWordRepositoryImpl @Inject constructor(
    private val dao: SavedWordDao,
) : SavedWordRepository {
    override fun observeAll(): Flow<List<SavedWordEntity>> = dao.observeAll()
    override fun observeByLang(code: String): Flow<List<SavedWordEntity>> = dao.observeByLang(code)
    override fun search(q: String): Flow<List<SavedWordEntity>> = dao.search(q)
    override fun countByLang(code: String): Flow<Int> = dao.countByLang(code)
    override suspend fun save(word: SavedWordEntity): Long = dao.insert(word)
    override suspend fun update(word: SavedWordEntity) = dao.update(word)
    override suspend fun delete(word: SavedWordEntity) = dao.delete(word)
}
