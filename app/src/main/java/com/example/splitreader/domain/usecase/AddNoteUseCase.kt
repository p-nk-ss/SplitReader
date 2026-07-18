package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Note
import com.example.splitreader.domain.repository.NoteRepository
import javax.inject.Inject

/** Creates or updates a note (or highlight) anchored to a specific paragraph in a book. */
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
        val now = System.currentTimeMillis()
        repository.upsert(
            Note(
                bookUri = bookUri,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                body = body,
                isHighlight = isHighlight,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
