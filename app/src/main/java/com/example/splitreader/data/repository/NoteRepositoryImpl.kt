package com.example.splitreader.data.repository

import com.example.splitreader.data.local.NoteDao
import com.example.splitreader.data.repository.mapper.toDomain
import com.example.splitreader.data.repository.mapper.toEntity
import com.example.splitreader.domain.model.Note
import com.example.splitreader.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NoteRepository {
    override fun observeForBook(uri: String): Flow<List<Note>> =
        dao.observeForBook(uri).map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(note: Note) = dao.upsert(note.toEntity())
    override suspend fun delete(note: Note) = dao.delete(note.toEntity())
}
