package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Note
import kotlinx.coroutines.flow.Flow

/** Manages paragraph-anchored notes and highlights within a book. */
interface NoteRepository {
    fun observeForBook(uri: String): Flow<List<Note>>
    suspend fun upsert(note: Note)
    suspend fun delete(note: Note)
}
