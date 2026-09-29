package com.example.splitreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.defaultOrientationLock
import com.example.splitreader.domain.repository.LegacyProgress
import com.example.splitreader.domain.repository.LegacyReadingPositionStore
import com.example.splitreader.domain.repository.ReadingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Persists per-book reading position and reader display/translation preferences via SharedPreferences. */
@Singleton
class ReadingProgressManager @Inject constructor(
    @ApplicationContext context: Context
) : ReadingPreferences, LegacyReadingPositionStore {
    private val prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)

    // Device form factor, read once. Drives the first-launch orientation default only.
    private val isTablet =
        context.resources.configuration.smallestScreenWidthDp >= ReadingDefaults.TABLET_MIN_SW_DP

    override fun saveProgress(bookUri: String, chapterIndex: Int, scrollPosition: Int, scrollOffset: Int) {
        prefs.edit()
            .putString("last_book_uri", bookUri)
            .putInt("last_chapter_$bookUri", chapterIndex)
            .putInt("last_scroll_${bookUri}_$chapterIndex", scrollPosition)
            .putInt("last_scroll_offset_${bookUri}_$chapterIndex", scrollOffset)
            .apply()
    }

    override fun getLastBookUri(): String? = prefs.getString("last_book_uri", null)

    override fun getLastChapter(bookUri: String): Int =
        prefs.getInt("last_chapter_$bookUri", 0)

    override fun getLastScrollPosition(bookUri: String, chapterIndex: Int): Int =
        prefs.getInt("last_scroll_${bookUri}_$chapterIndex", 0)

    override fun getLastScrollOffset(bookUri: String, chapterIndex: Int): Int =
        prefs.getInt("last_scroll_offset_${bookUri}_$chapterIndex", 0)

    override fun saveReadingPosition(bookUri: String, position: ReadingPosition) {
        prefs.edit().putString("last_book_uri", bookUri).putPosition(bookUri, position).apply()
    }

    override fun getReadingPosition(bookUri: String): ReadingPosition = ReadingPosition(
        chapter = prefs.getInt("last_chapter_$bookUri", 0),
        paragraph = prefs.getInt("last_paragraph_$bookUri", 0),
        offset = prefs.getInt("last_paragraph_offset_$bookUri", 0),
    )

    // The chapter deliberately keeps the legacy "last_chapter_" key: its meaning never changed, so
    // Home shows chapter progress for books that have not been reopened (migrated) yet.
    private fun SharedPreferences.Editor.putPosition(bookUri: String, p: ReadingPosition) =
        putInt("last_chapter_$bookUri", p.chapter)
            .putInt("last_paragraph_$bookUri", p.paragraph)
            .putInt("last_paragraph_offset_$bookUri", p.offset)

    override fun isReadingPositionMigrated(bookUri: String): Boolean =
        prefs.getBoolean("position_v2_$bookUri", false)

    override fun legacyProgress(bookUri: String): LegacyProgress? {
        val chapter = prefs.getInt("last_chapter_$bookUri", 0)
        val key = "last_scroll_${bookUri}_$chapter"
        if (!prefs.contains(key)) return null
        return LegacyProgress(chapter, prefs.getInt(key, 0), prefs.getInt("last_scroll_offset_${bookUri}_$chapter", 0))
    }

    override fun completeReadingPositionMigration(bookUri: String, position: ReadingPosition?) {
        val edit = prefs.edit()
        if (position != null) edit.putPosition(bookUri, position)
        legacyScrollKeys(bookUri).forEach { edit.remove(it) }
        edit.putBoolean("position_v2_$bookUri", true).apply()
    }

    /** `last_scroll_<uri>_<n>` and `last_scroll_offset_<uri>_<n>` — suffix must be a bare chapter
     *  number, so "/b/a" never matches "/b/a_1"'s keys. */
    private fun legacyScrollKeys(bookUri: String): List<String> {
        val prefixes = listOf("last_scroll_${bookUri}_", "last_scroll_offset_${bookUri}_")
        return prefs.all.keys.filter { key ->
            prefixes.any { key.startsWith(it) && key.removePrefix(it).toIntOrNull() != null }
        }
    }

    /** Persists the paragraph the reader last stopped on, shown in the Library's Continue Reading hero. */
    override fun saveExcerpt(bookUri: String, text: String) =
        prefs.edit().putString("last_excerpt_$bookUri", text).apply()

    override fun getExcerpt(bookUri: String): String? =
        prefs.getString("last_excerpt_$bookUri", null)

    /** Marks a book as finished once the reader has scrolled to the end of its last chapter. */
    override fun markFinished(bookUri: String) =
        prefs.edit().putBoolean("finished_$bookUri", true).apply()

    override fun isFinished(bookUri: String): Boolean =
        prefs.getBoolean("finished_$bookUri", false)

    override fun clearProgress(bookUri: String) {
        prefs.edit()
            .remove("last_chapter_$bookUri")
            .remove("last_paragraph_$bookUri")
            .remove("last_paragraph_offset_$bookUri")
            .remove("finished_$bookUri")
            .remove("last_excerpt_$bookUri")
            .apply()
    }

    override fun saveTargetLanguage(language: Language) {
        prefs.edit()
            .putString("target_language", language.code)
            .apply()
    }

    override fun getTargetLanguage(): Language {
        val code = prefs.getString("target_language", Language.ENGLISH.code)
        return Language.entries.find { it.code == code } ?: Language.ENGLISH
    }

    override fun saveNavigationSideLeft(isLeft: Boolean) {
        prefs.edit().putBoolean("navigation_side_left", isLeft).apply()
    }

    override fun isNavigationLeft(): Boolean =
        prefs.getBoolean("navigation_side_left", ReadingDefaults.NAVIGATION_SIDE_LEFT)

    private val _readerThemeName = MutableStateFlow(getReaderThemeName())

    /** Reactive stream of the persisted reader-theme name, so the whole app
     *  (not just the reading pane) can follow the selected theme. */
    override val readerThemeName: StateFlow<String> = _readerThemeName.asStateFlow()

    override fun saveReaderTheme(themeName: String) {
        prefs.edit().putString("reader_theme", themeName).apply()
        _readerThemeName.value = themeName
    }

    override fun getReaderThemeName(): String =
        prefs.getString("reader_theme", ReadingDefaults.READER_THEME) ?: ReadingDefaults.READER_THEME

    private val _orientationLock = MutableStateFlow(getOrientationLock())

    /** Reactive stream of the orientation policy so [MainActivity] can re-apply it without a restart. */
    override val orientationLock: StateFlow<OrientationLock> = _orientationLock.asStateFlow()

    override fun saveOrientationLock(lock: OrientationLock) {
        prefs.edit().putString("orientation_lock", lock.name).apply()
        _orientationLock.value = lock
    }

    /**
     * No stored value ⇒ the user has not chosen ⇒ derive from form factor every read. The seed is
     * deliberately NOT written back on read, so the choice stays "unset" (and would re-derive
     * correctly if prefs were restored onto a different-form-factor device) until an explicit save.
     */
    override fun getOrientationLock(): OrientationLock {
        val name = prefs.getString("orientation_lock", null)
            ?: return defaultOrientationLock(isTablet)
        return OrientationLock.entries.find { it.name == name } ?: defaultOrientationLock(isTablet)
    }

    override fun saveLineHeightMultiplier(multiplier: Float) {
        prefs.edit().putFloat("line_height_multiplier", multiplier).apply()
    }

    override fun getLineHeightMultiplier(): Float =
        prefs.getFloat("line_height_multiplier", ReadingDefaults.LINE_HEIGHT)

    override fun saveSplitRatio(ratio: Float) {
        prefs.edit().putFloat("split_ratio", ratio).apply()
    }

    override fun getSplitRatio(): Float = prefs.getFloat("split_ratio", ReadingDefaults.SPLIT_RATIO)

    override fun saveVerticalSplitRatio(ratio: Float) {
        prefs.edit().putFloat("vertical_split_ratio", ratio).apply()
    }

    override fun getVerticalSplitRatio(): Float =
        prefs.getFloat("vertical_split_ratio", ReadingDefaults.VERTICAL_SPLIT_RATIO)

    override fun savePortraitHintDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean("portrait_hint_dismissed", dismissed).apply()
    }

    override fun getPortraitHintDismissed(): Boolean =
        prefs.getBoolean("portrait_hint_dismissed", ReadingDefaults.PORTRAIT_HINT_DISMISSED)

    override fun saveShowTranslation(show: Boolean) {
        prefs.edit().putBoolean("show_translation", show).apply()
    }

    override fun getShowTranslation(): Boolean =
        prefs.getBoolean("show_translation", ReadingDefaults.SHOW_TRANSLATION)

    override fun saveShowIllustrations(show: Boolean) {
        prefs.edit().putBoolean("show_illustrations", show).apply()
    }

    override fun getShowIllustrations(): Boolean =
        prefs.getBoolean("show_illustrations", ReadingDefaults.SHOW_ILLUSTRATIONS)

    override fun saveHorizontalMargin(margin: Float) {
        prefs.edit().putFloat("horizontal_margin", margin).apply()
    }

    override fun getHorizontalMargin(): Float =
        prefs.getFloat("horizontal_margin", ReadingDefaults.HORIZONTAL_MARGIN)

    override fun setTranslatorProvider(provider: TranslationProvider) {
        prefs.edit().putString("translator_provider", provider.name).apply()
    }

    override fun getTranslatorProvider(): TranslationProvider =
        TranslationProvider.fromName(prefs.getString("translator_provider", null))

    // ── Typography (reading panes) ─────────────────────────────────────────────

    override fun saveTextSize(size: Float) {
        prefs.edit().putFloat("text_size", size).apply()
    }

    override fun getTextSize(): Float = prefs.getFloat("text_size", ReadingDefaults.TEXT_SIZE)

    /** Persisted reading typeface name; falls back to SERIF for unknown/legacy values. */
    override fun saveReadingFont(name: String) {
        prefs.edit().putString("reading_font", name).apply()
    }

    override fun getReadingFontName(): String =
        prefs.getString("reading_font", ReadingDefaults.READING_FONT) ?: ReadingDefaults.READING_FONT

    override fun saveParagraphSpacing(spacing: Float) {
        prefs.edit().putFloat("paragraph_spacing", spacing).apply()
    }

    override fun getParagraphSpacing(): Float =
        prefs.getFloat("paragraph_spacing", ReadingDefaults.PARAGRAPH_SPACING)

    override fun saveLetterSpacing(spacing: Float) {
        prefs.edit().putFloat("letter_spacing", spacing).apply()
    }

    override fun getLetterSpacing(): Float =
        prefs.getFloat("letter_spacing", ReadingDefaults.LETTER_SPACING)

    override fun saveTextIndent(indent: Float) {
        prefs.edit().putFloat("text_indent", indent).apply()
    }

    override fun getTextIndent(): Float = prefs.getFloat("text_indent", ReadingDefaults.TEXT_INDENT)

    override fun saveJustifyText(justify: Boolean) {
        prefs.edit().putBoolean("justify_text", justify).apply()
    }

    override fun getJustifyText(): Boolean = prefs.getBoolean("justify_text", ReadingDefaults.JUSTIFY_TEXT)

    // ── Read-aloud (TTS) ───────────────────────────────────────────────────────

    override fun saveTtsRate(rate: Float) {
        prefs.edit().putFloat("tts_rate", rate).apply()
    }

    override fun getTtsRate(): Float = prefs.getFloat("tts_rate", ReadingDefaults.TTS_RATE)

    override fun saveTtsPitch(pitch: Float) {
        prefs.edit().putFloat("tts_pitch", pitch).apply()
    }

    override fun getTtsPitch(): Float = prefs.getFloat("tts_pitch", ReadingDefaults.TTS_PITCH)
}
