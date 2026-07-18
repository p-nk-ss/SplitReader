package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.NoteEntity
import com.example.splitreader.domain.model.Note

fun NoteEntity.toDomain(): Note =
    Note(bookUri, chapterIndex, paragraphIndex, body, isHighlight, createdAt, updatedAt)

fun Note.toEntity(): NoteEntity =
    NoteEntity(bookUri, chapterIndex, paragraphIndex, body, isHighlight, createdAt, updatedAt)
