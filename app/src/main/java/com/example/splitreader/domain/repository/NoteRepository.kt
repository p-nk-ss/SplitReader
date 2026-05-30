package com.example.splitreader.domain.repository

import com.example.splitreader.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow

/** Manages paragraph-anchored notes and highlights within a book. */
interface NoteRepository {
    fun observeForBook(uri: String): Flow<List<NoteEntity>>
    suspend fun upsert(note: NoteEntity)
    suspend fun delete(note: NoteEntity)
}
