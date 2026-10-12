package com.example.splitreader.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReadingAppearance
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

    private val _appearance = MutableStateFlow(readAppearance())

    /** See `ReadingPreferences.appearance`. */
    override val appearance: StateFlow<ReadingAppearance> = _appearance.asStateFlow()

    /** Builds the appearance from the stored keys (missing key -> [ReadingDefaults]), then sanitizes it. */
    private fun readAppearance(): ReadingAppearance = ReadingAppearance(
        theme = ReadingAppearance.themeFromName(prefs.getString("reader_theme", null)),
        font = ReadingAppearance.fontFromName(prefs.getString("reading_font", null)),
        textSize = prefs.getFloat("text_size", ReadingDefaults.TEXT_SIZE),
        lineHeight = prefs.getFloat("line_height_multiplier", ReadingDefaults.LINE_HEIGHT),
        letterSpacing = prefs.getFloat("letter_spacing", ReadingDefaults.LETTER_SPACING),
        textIndent = prefs.getFloat("text_indent", ReadingDefaults.TEXT_INDENT),
        paragraphSpacing = prefs.getFloat("paragraph_spacing", ReadingDefaults.PARAGRAPH_SPACING),
        justify = prefs.getBoolean("justify_text", ReadingDefaults.JUSTIFY_TEXT),
        splitRatio = prefs.getFloat("split_ratio", ReadingDefaults.SPLIT_RATIO),
        verticalSplitRatio = prefs.getFloat("vertical_split_ratio", ReadingDefaults.VERTICAL_SPLIT_RATIO),
        horizontalMargin = prefs.getFloat("horizontal_margin", ReadingDefaults.HORIZONTAL_MARGIN),
        showTranslation = prefs.getBoolean("show_translation", ReadingDefaults.SHOW_TRANSLATION),
        showIllustrations = prefs.getBoolean("show_illustrations", ReadingDefaults.SHOW_ILLUSTRATIONS),
        orientationLock = readOrientationLock(),
    ).sanitized()

    /**
     * No stored value ⇒ the user has not chosen ⇒ derive from form factor every read. The seed is
     * deliberately NOT written back on read (nor by an unrelated [updateAppearance]), so the choice
     * stays "unset" (and would re-derive correctly if prefs were restored onto a
     * different-form-factor device) until an explicit change.
     */
    private fun readOrientationLock(): OrientationLock {
        val name = prefs.getString("orientation_lock", null)
            ?: return defaultOrientationLock(isTablet)
        return OrientationLock.entries.find { it.name == name } ?: defaultOrientationLock(isTablet)
    }

    @Synchronized
    override fun updateAppearance(transform: (ReadingAppearance) -> ReadingAppearance) {
        val cur = _appearance.value
        val next = transform(cur).sanitized()
        if (next == cur) return
        prefs.edit().apply {
            if (next.theme != cur.theme) putString("reader_theme", next.theme.name)
            if (next.font != cur.font) putString("reading_font", next.font.name)
            if (next.textSize != cur.textSize) putFloat("text_size", next.textSize)
            if (next.lineHeight != cur.lineHeight) putFloat("line_height_multiplier", next.lineHeight)
            if (next.letterSpacing != cur.letterSpacing) putFloat("letter_spacing", next.letterSpacing)
            if (next.textIndent != cur.textIndent) putFloat("text_indent", next.textIndent)
            if (next.paragraphSpacing != cur.paragraphSpacing) putFloat("paragraph_spacing", next.paragraphSpacing)
            if (next.justify != cur.justify) putBoolean("justify_text", next.justify)
            if (next.splitRatio != cur.splitRatio) putFloat("split_ratio", next.splitRatio)
            if (next.verticalSplitRatio != cur.verticalSplitRatio) {
                putFloat("vertical_split_ratio", next.verticalSplitRatio)
            }
            if (next.horizontalMargin != cur.horizontalMargin) putFloat("horizontal_margin", next.horizontalMargin)
            if (next.showTranslation != cur.showTranslation) putBoolean("show_translation", next.showTranslation)
            if (next.showIllustrations != cur.showIllustrations) {
                putBoolean("show_illustrations", next.showIllustrations)
            }
            if (next.orientationLock != cur.orientationLock) putString("orientation_lock", next.orientationLock.name)
        }.apply()
        _appearance.value = next
    }

    override fun getLastBookUri(): String? = prefs.getString("last_book_uri", null)

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
        // commit(), not apply(): this write must be durable before returning, see the KDoc on
        // LegacyReadingPositionStore.completeReadingPositionMigration.
        edit.putBoolean("position_v2_$bookUri", true).commit()
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

    override fun savePortraitHintDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean("portrait_hint_dismissed", dismissed).apply()
    }

    override fun getPortraitHintDismissed(): Boolean =
        prefs.getBoolean("portrait_hint_dismissed", ReadingDefaults.PORTRAIT_HINT_DISMISSED)

    override fun setTranslatorProvider(provider: TranslationProvider) {
        prefs.edit().putString("translator_provider", provider.name).apply()
    }

    override fun getTranslatorProvider(): TranslationProvider =
        TranslationProvider.fromName(prefs.getString("translator_provider", null))

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
