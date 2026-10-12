package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import kotlinx.coroutines.flow.StateFlow

/** Persists per-book reading position and reader display/translation preferences. */
interface ReadingPreferences {
    fun getLastBookUri(): String?
    /** Persists [position] as [bookUri]'s progress and remembers [bookUri] as the last-read book. */
    fun saveReadingPosition(bookUri: String, position: ReadingPosition)
    /** [bookUri]'s saved Reading position; [ReadingPosition.START] if it was never read. */
    fun getReadingPosition(bookUri: String): ReadingPosition
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
    val orientationLock: StateFlow<OrientationLock>
    fun saveOrientationLock(lock: OrientationLock)
    fun getOrientationLock(): OrientationLock
    fun saveLineHeightMultiplier(multiplier: Float)
    fun getLineHeightMultiplier(): Float
    fun saveSplitRatio(ratio: Float)
    fun getSplitRatio(): Float

    fun saveVerticalSplitRatio(ratio: Float)
    fun getVerticalSplitRatio(): Float

    fun savePortraitHintDismissed(dismissed: Boolean)
    fun getPortraitHintDismissed(): Boolean
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

    /**
     * Single source of truth for the reading-pane appearance (theme, typography, layout, the two
     * reader-wide toggles and orientation). Alongside the individual getters/setters above during
     * the migration (see `docs/superpowers/specs/2026-10-12-reading-appearance-design.md` §3.1) —
     * every old setter keeps this in sync, so the two APIs never disagree.
     */
    val appearance: StateFlow<ReadingAppearance>

    /** Applies [transform] to the current [appearance], sanitizes it, and persists + publishes the result. */
    fun updateAppearance(transform: (ReadingAppearance) -> ReadingAppearance)
}
