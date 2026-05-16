package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.model.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)

    fun saveProgress(bookUri: String, chapterIndex: Int, scrollPosition: Int) {
        prefs.edit()
            .putString("last_book_uri", bookUri)
            .putInt("last_chapter_$bookUri", chapterIndex)
            .putInt("last_scroll_${bookUri}_$chapterIndex", scrollPosition)
            .apply()
    }

    fun getLastBookUri(): String? = prefs.getString("last_book_uri", null)

    fun getLastChapter(bookUri: String): Int =
        prefs.getInt("last_chapter_$bookUri", 0)

    fun getLastScrollPosition(bookUri: String, chapterIndex: Int): Int =
        prefs.getInt("last_scroll_${bookUri}_$chapterIndex", 0)

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
}
