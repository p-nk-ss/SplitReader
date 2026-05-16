package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Success(
        val book: Book,
        val currentChapterIndex: Int,
        val sourceLanguage: Language,
        val targetLanguage: Language,
        val translationState: TranslationState,
        val translatedParagraphs: List<String>,
        val pendingScrollPosition: Int = -1,
        val textSize: Float = 16f,
        val navigationSide: NavigationSide = NavigationSide.RIGHT,
    ) : ReaderUiState {
        val currentChapter: Chapter get() = book.chapters[currentChapterIndex]
        val canGoPrevious: Boolean get() = currentChapterIndex > 0
        val canGoNext: Boolean get() = currentChapterIndex < book.chapters.size - 1
    }
}
