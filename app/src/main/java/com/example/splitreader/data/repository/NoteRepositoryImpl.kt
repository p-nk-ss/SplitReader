package com.example.splitreader.data.repository

import com.example.splitreader.data.local.NoteDao
import com.example.splitreader.data.local.NoteEntity
import com.example.splitreader.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NoteRepository {
    override fun observeForBook(uri: String): Flow<List<NoteEntity>> = dao.observeForBook(uri)
    override suspend fun upsert(note: NoteEntity) = dao.upsert(note)
    override suspend fun delete(note: NoteEntity) = dao.delete(note)
}
