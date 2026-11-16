package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import kotlinx.coroutines.flow.StateFlow

/** Persists per-book reading position and reader display/translation preferences. */
interface ReadingPreferences {
    /** Persists [position] as [bookUri]'s progress. */
    fun saveReadingPosition(bookUri: String, position: ReadingPosition)
    /** [bookUri]'s saved Reading position; [ReadingPosition.START] if it was never read. */
    fun getReadingPosition(bookUri: String): ReadingPosition
    /** [bookUri]'s reading progress (Book.progressAt), saved with the position for the Library cards. */
    fun saveProgress(bookUri: String, progress: Float)
    /** Null for a book whose progress was never saved (read before this existed): callers fall back. */
    fun getProgress(bookUri: String): Float?
    fun saveExcerpt(bookUri: String, text: String)
    fun getExcerpt(bookUri: String): String?
    fun markFinished(bookUri: String)
    fun isFinished(bookUri: String): Boolean
    /**
     * Forgets everything stored for [bookUri] (position, progress, excerpt, finished, its source
     * language, migration state), so the same file imported again starts as a new book.
     */
    fun forgetBook(bookUri: String)

    /**
     * Copies everything stored for [from] (what [forgetBook] would forget) to [to], replacing what
     * [to] had: the Same book came back under another uri. [from] keeps its copy until [forgetBook].
     * Synchronous disk write: call off the main thread.
     */
    fun copyBook(from: String, to: String)
    /** The language the reader chose for [bookUri]'s text; null = detect it (the default). */
    fun getSourceLanguage(bookUri: String): Language?
    fun saveSourceLanguage(bookUri: String, language: Language?)
    fun saveTargetLanguage(language: Language)
    fun getTargetLanguage(): Language
    fun savePortraitHintDismissed(dismissed: Boolean)
    fun getPortraitHintDismissed(): Boolean
    fun setTranslatorProvider(provider: TranslationProvider)
    /**
     * The translator the reader chose; null if they never chose one (the default applies) -- or chose
     * one the app no longer has ([retiredTranslatorProvider]).
     */
    fun getTranslatorProvider(): TranslationProvider?

    /** The stored choice when it names a translator the app no longer has (e.g. "LIBRE_TRANSLATE"); else null. */
    fun retiredTranslatorProvider(): String?

    /** Forgets the reader's translator choice: the default applies again. */
    fun clearTranslatorProvider()

    /** A one-time message about the reader's translator (a retired one was replaced), until it is shown. */
    fun getTranslatorNotice(): String?
    fun setTranslatorNotice(text: String?)
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
