package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.TranslationProvider
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
) : ReadingPreferences {
    private val prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)

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

    override fun saveLineHeightMultiplier(multiplier: Float) {
        prefs.edit().putFloat("line_height_multiplier", multiplier).apply()
    }

    override fun getLineHeightMultiplier(): Float =
        prefs.getFloat("line_height_multiplier", ReadingDefaults.LINE_HEIGHT)

    override fun saveSplitRatio(ratio: Float) {
        prefs.edit().putFloat("split_ratio", ratio).apply()
    }

    override fun getSplitRatio(): Float = prefs.getFloat("split_ratio", ReadingDefaults.SPLIT_RATIO)

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
