package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.BookmarkRepository
import javax.inject.Inject

class ToggleBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(bookUri: String, chapterIndex: Int, paragraphIndex: Int) {
        repository.toggle(bookUri, chapterIndex, paragraphIndex)
    }
}
