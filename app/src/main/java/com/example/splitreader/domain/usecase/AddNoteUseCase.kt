package com.example.splitreader.domain.usecase

import com.example.splitreader.data.local.NoteEntity
import com.example.splitreader.domain.repository.NoteRepository
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(
        bookUri: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        body: String,
        isHighlight: Boolean = false,
    ) {
        repository.upsert(
            NoteEntity(
                bookUri = bookUri,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                body = body,
                isHighlight = isHighlight,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
