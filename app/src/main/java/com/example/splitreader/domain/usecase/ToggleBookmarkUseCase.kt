package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.BookmarkRepository
import javax.inject.Inject

/** Toggles a bookmark on the given paragraph: adds it if absent, removes it if present. */
class ToggleBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(bookUri: String, chapterIndex: Int, paragraphIndex: Int) {
        repository.toggle(bookUri, chapterIndex, paragraphIndex)
    }
}
