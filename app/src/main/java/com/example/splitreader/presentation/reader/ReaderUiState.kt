package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.presentation.theme.ReaderThemeKey

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Success(
        val book: Book,
        val currentChapterIndex: Int,
        val sourceLanguage: Language,
        val targetLanguage: Language,
        val translationState: TranslationState,
        val chapterTranslations: Map<Int, List<String>>,
        val pendingScrollPosition: Int = -1,
        val pendingScrollOffset: Int = 0,
        val textSize: Float = 16f,
        val lineHeightMultiplier: Float = 1.5f,
        val splitRatio: Float = 0.5f,
        val showTranslation: Boolean = true,
        val readerTheme: ReaderThemeKey = ReaderThemeKey.PAPER,
        val navigationSide: NavigationSide = NavigationSide.RIGHT,
        val horizontalMargin: Float = 12f,
    ) : ReaderUiState {
        val currentChapter: Chapter
            get() = book.chapters[currentChapterIndex.coerceIn(0, book.chapters.size - 1)]
    }
}
