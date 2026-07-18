package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import kotlinx.coroutines.flow.StateFlow

/** Persists per-book reading position and reader display/translation preferences. */
interface ReadingPreferences {
    fun saveProgress(bookUri: String, chapterIndex: Int, scrollPosition: Int, scrollOffset: Int = 0)
    fun getLastBookUri(): String?
    fun getLastChapter(bookUri: String): Int
    fun getLastScrollPosition(bookUri: String, chapterIndex: Int): Int
    fun getLastScrollOffset(bookUri: String, chapterIndex: Int): Int
    fun saveExcerpt(bookUri: String, text: String)
    fun getExcerpt(bookUri: String): String?
    fun markFinished(bookUri: String)
    fun isFinished(bookUri: String): Boolean
    fun clearProgress(bookUri: String)
    fun saveTargetLanguage(language: Language)
    fun getTargetLanguage(): Language
    fun saveNavigationSideLeft(isLeft: Boolean)
    fun isNavigationLeft(): Boolean
    val readerThemeName: StateFlow<String>
    fun saveReaderTheme(themeName: String)
    fun getReaderThemeName(): String
    fun saveLineHeightMultiplier(multiplier: Float)
    fun getLineHeightMultiplier(): Float
    fun saveSplitRatio(ratio: Float)
    fun getSplitRatio(): Float
    fun saveShowTranslation(show: Boolean)
    fun getShowTranslation(): Boolean
    fun saveShowIllustrations(show: Boolean)
    fun getShowIllustrations(): Boolean
    fun saveHorizontalMargin(margin: Float)
    fun getHorizontalMargin(): Float
    fun setTranslatorProvider(provider: TranslationProvider)
    fun getTranslatorProvider(): TranslationProvider
    fun saveTextSize(size: Float)
    fun getTextSize(): Float
    fun saveReadingFont(name: String)
    fun getReadingFontName(): String
    fun saveParagraphSpacing(spacing: Float)
    fun getParagraphSpacing(): Float
    fun saveLetterSpacing(spacing: Float)
    fun getLetterSpacing(): Float
    fun saveTextIndent(indent: Float)
    fun getTextIndent(): Float
    fun saveJustifyText(justify: Boolean)
    fun getJustifyText(): Boolean
    fun saveTtsRate(rate: Float)
    fun getTtsRate(): Float
    fun saveTtsPitch(pitch: Float)
    fun getTtsPitch(): Float
}
