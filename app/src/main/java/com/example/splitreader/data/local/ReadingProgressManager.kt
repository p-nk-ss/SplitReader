package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists per-book reading position and reader display/translation preferences via SharedPreferences. */
@Singleton
class ReadingProgressManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)

    fun saveProgress(bookUri: String, chapterIndex: Int, scrollPosition: Int, scrollOffset: Int = 0) {
        prefs.edit()
            .putString("last_book_uri", bookUri)
            .putInt("last_chapter_$bookUri", chapterIndex)
            .putInt("last_scroll_${bookUri}_$chapterIndex", scrollPosition)
            .putInt("last_scroll_offset_${bookUri}_$chapterIndex", scrollOffset)
            .apply()
    }

    fun getLastBookUri(): String? = prefs.getString("last_book_uri", null)

    fun getLastChapter(bookUri: String): Int =
        prefs.getInt("last_chapter_$bookUri", 0)

    fun getLastScrollPosition(bookUri: String, chapterIndex: Int): Int =
        prefs.getInt("last_scroll_${bookUri}_$chapterIndex", 0)

    fun getLastScrollOffset(bookUri: String, chapterIndex: Int): Int =
        prefs.getInt("last_scroll_offset_${bookUri}_$chapterIndex", 0)

    fun clearProgress(bookUri: String) {
        prefs.edit()
            .remove("last_chapter_$bookUri")
            .apply()
    }

    fun saveTargetLanguage(language: Language) {
        prefs.edit()
            .putString("target_language", language.code)
            .apply()
    }

    fun getTargetLanguage(): Language {
        val code = prefs.getString("target_language", Language.ENGLISH.code)
        return Language.entries.find { it.code == code } ?: Language.ENGLISH
    }

    fun saveNavigationSideLeft(isLeft: Boolean) {
        prefs.edit().putBoolean("navigation_side_left", isLeft).apply()
    }

    fun isNavigationLeft(): Boolean = prefs.getBoolean("navigation_side_left", false)

    fun saveReaderTheme(themeName: String) {
        prefs.edit().putString("reader_theme", themeName).apply()
    }

    fun getReaderThemeName(): String = prefs.getString("reader_theme", "DEFAULT") ?: "DEFAULT"

    fun saveLineHeightMultiplier(multiplier: Float) {
        prefs.edit().putFloat("line_height_multiplier", multiplier).apply()
    }

    fun getLineHeightMultiplier(): Float = prefs.getFloat("line_height_multiplier", 1.5f)

    fun saveSplitRatio(ratio: Float) {
        prefs.edit().putFloat("split_ratio", ratio).apply()
    }

    fun getSplitRatio(): Float = prefs.getFloat("split_ratio", 0.5f)

    fun saveShowTranslation(show: Boolean) {
        prefs.edit().putBoolean("show_translation", show).apply()
    }

    fun getShowTranslation(): Boolean = prefs.getBoolean("show_translation", true)

    fun saveHorizontalMargin(margin: Float) {
        prefs.edit().putFloat("horizontal_margin", margin).apply()
    }

    fun getHorizontalMargin(): Float = prefs.getFloat("horizontal_margin", 12f)

    fun setTranslatorProvider(provider: TranslationProvider) {
        prefs.edit().putString("translator_provider", provider.name).apply()
    }

    fun getTranslatorProvider(): TranslationProvider =
        TranslationProvider.fromName(prefs.getString("translator_provider", null))
}
