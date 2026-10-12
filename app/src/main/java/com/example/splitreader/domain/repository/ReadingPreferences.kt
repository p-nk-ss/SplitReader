package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
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
    fun savePortraitHintDismissed(dismissed: Boolean)
    fun getPortraitHintDismissed(): Boolean
    fun setTranslatorProvider(provider: TranslationProvider)
    fun getTranslatorProvider(): TranslationProvider
    fun saveTtsRate(rate: Float)
    fun getTtsRate(): Float
    fun saveTtsPitch(pitch: Float)
    fun getTtsPitch(): Float

    /**
     * Single source of truth for the reading-pane appearance (theme, typography, layout, the two
     * reader-wide toggles and orientation); see
     * `docs/superpowers/specs/2026-10-12-reading-appearance-design.md` §3.1.
     */
    val appearance: StateFlow<ReadingAppearance>

    /** Applies [transform] to the current [appearance], sanitizes it, and persists + publishes the result. */
    fun updateAppearance(transform: (ReadingAppearance) -> ReadingAppearance)
}
