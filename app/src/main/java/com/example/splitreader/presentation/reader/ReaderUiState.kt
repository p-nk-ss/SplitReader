package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.ReadingFont

enum class SelectionType { WORD, SENTENCE }

/**
 * Bundle of typography values applied to the reading panes, so the values can be threaded as a
 * single argument through [BookSpread] and the paragraph composables.
 */
data class ReadingStyle(
    val font: ReadingFont = ReadingFont.SERIF,
    val textSize: Float = ReadingDefaults.TEXT_SIZE,
    val lineHeightMultiplier: Float = ReadingDefaults.LINE_HEIGHT,
    val letterSpacing: Float = ReadingDefaults.LETTER_SPACING,
    val textIndent: Float = ReadingDefaults.TEXT_INDENT,
    val paragraphSpacing: Float = ReadingDefaults.PARAGRAPH_SPACING,
    val justify: Boolean = ReadingDefaults.JUSTIFY_TEXT,
)

data class WordSelection(
    val word: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val translation: String? = null,
    val selectionType: SelectionType = SelectionType.WORD,
)

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
        val textSize: Float = ReadingDefaults.TEXT_SIZE,
        val lineHeightMultiplier: Float = ReadingDefaults.LINE_HEIGHT,
        val readingFont: ReadingFont = ReadingFont.SERIF,
        val letterSpacing: Float = ReadingDefaults.LETTER_SPACING,
        val textIndent: Float = ReadingDefaults.TEXT_INDENT,
        val paragraphSpacing: Float = ReadingDefaults.PARAGRAPH_SPACING,
        val justifyText: Boolean = ReadingDefaults.JUSTIFY_TEXT,
        val splitRatio: Float = ReadingDefaults.SPLIT_RATIO,
        val showTranslation: Boolean = ReadingDefaults.SHOW_TRANSLATION,
        val showIllustrations: Boolean = ReadingDefaults.SHOW_ILLUSTRATIONS,
        val readerTheme: ReaderThemeKey = ReaderThemeKey.PAPER,
        val navigationSide: NavigationSide = NavigationSide.RIGHT,
        val horizontalMargin: Float = ReadingDefaults.HORIZONTAL_MARGIN,
        val bookmarks: List<Bookmark> = emptyList(),
        val isCurrentPositionBookmarked: Boolean = false,
        val wordSelection: WordSelection? = null,
        val translatorProvider: TranslationProvider = TranslationProvider.MLKIT,
        val translatorConfig: TranslatorConfigState =
            TranslatorConfigState(current = TranslationProvider.MLKIT, configs = emptyMap()),
    ) : ReaderUiState {
        val readingStyle: ReadingStyle
            get() = ReadingStyle(
                font = readingFont,
                textSize = textSize,
                lineHeightMultiplier = lineHeightMultiplier,
                letterSpacing = letterSpacing,
                textIndent = textIndent,
                paragraphSpacing = paragraphSpacing,
                justify = justifyText,
            )

        val currentChapter: Chapter
            get() = book.chapters[currentChapterIndex.coerceIn(0, book.chapters.size - 1)]
    }
}
