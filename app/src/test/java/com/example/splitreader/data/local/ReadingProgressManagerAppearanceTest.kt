package com.example.splitreader.data.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReaderThemeKey
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.ReadingFont
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `ReadingProgressManager.appearance`/`updateAppearance` over the existing `"reading_progress"`
 * keys. Real SharedPreferences under Robolectric — the plausible defects are key names (a field
 * written under the wrong key, corrupting a sibling field) and dropped sanitization, which a fake
 * cannot observe. Raw prefs are seeded/read exactly as the individual getters/setters already do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingProgressManagerAppearanceTest {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var manager: ReadingProgressManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun newManager(): ReadingProgressManager =
        ReadingProgressManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `values stored under the existing keys are read back`() {
        prefs.edit()
            .putString("reader_theme", "NIGHT")
            .putString("reading_font", "MONO")
            .putFloat("text_size", 20f)
            .putFloat("split_ratio", 0.4f)
            .putFloat("horizontal_margin", 12f)
            .putBoolean("show_translation", false)
            .putString("orientation_lock", "LANDSCAPE")
            .commit()

        manager = newManager()
        val appearance = manager.appearance.value

        assertEquals(ReaderThemeKey.NIGHT, appearance.theme)
        assertEquals(ReadingFont.MONO, appearance.font)
        assertEquals(20f, appearance.textSize, 1e-6f)
        assertEquals(0.4f, appearance.splitRatio, 1e-6f)
        assertEquals(12f, appearance.horizontalMargin, 1e-6f)
        assertEquals(false, appearance.showTranslation)
        assertEquals(OrientationLock.LANDSCAPE, appearance.orientationLock)
    }

    @Test
    fun `a stored DEFAULT theme and junk numbers come back sanitized`() {
        prefs.edit()
            .putString("reader_theme", "DEFAULT")
            .putFloat("text_size", 999f)
            .commit()

        manager = newManager()
        val appearance = manager.appearance.value

        assertEquals(ReaderThemeKey.PAPER, appearance.theme)
        assertEquals(ReadingDefaults.TEXT_SIZE_RANGE.endInclusive, appearance.textSize, 1e-6f)
    }

    @Test
    fun `updateAppearance saves and publishes`() {
        manager = newManager()

        manager.updateAppearance { it.copy(textSize = 22f) }

        assertEquals(22f, manager.appearance.value.textSize, 1e-6f)

        val fresh = newManager()
        assertEquals(22f, fresh.appearance.value.textSize, 1e-6f)
    }

    @Test
    fun `updating one field leaves the other keys alone`() {
        prefs.edit()
            .putString("reader_theme", "NIGHT")
            .putString("reading_font", "MONO")
            .putFloat("text_size", 20f)
            .putFloat("line_height_multiplier", 1.8f)
            .putFloat("letter_spacing", 0.5f)
            .putFloat("text_indent", 10f)
            .putFloat("paragraph_spacing", 20f)
            .putBoolean("justify_text", false)
            .putFloat("split_ratio", 0.4f)
            .putFloat("vertical_split_ratio", 0.45f)
            .putFloat("horizontal_margin", 12f)
            .putBoolean("show_translation", false)
            .putBoolean("show_illustrations", false)
            .putString("orientation_lock", "LANDSCAPE")
            .commit()
        manager = newManager()

        manager.updateAppearance { it.copy(letterSpacing = 1.2f) }

        assertEquals(1.2f, prefs.getFloat("letter_spacing", -1f), 1e-6f)
        assertEquals("NIGHT", prefs.getString("reader_theme", null))
        assertEquals("MONO", prefs.getString("reading_font", null))
        assertEquals(20f, prefs.getFloat("text_size", -1f), 1e-6f)
        assertEquals(1.8f, prefs.getFloat("line_height_multiplier", -1f), 1e-6f)
        assertEquals(10f, prefs.getFloat("text_indent", -1f), 1e-6f)
        assertEquals(20f, prefs.getFloat("paragraph_spacing", -1f), 1e-6f)
        assertEquals(false, prefs.getBoolean("justify_text", true))
        assertEquals(0.4f, prefs.getFloat("split_ratio", -1f), 1e-6f)
        assertEquals(0.45f, prefs.getFloat("vertical_split_ratio", -1f), 1e-6f)
        assertEquals(12f, prefs.getFloat("horizontal_margin", -1f), 1e-6f)
        assertEquals(false, prefs.getBoolean("show_translation", true))
        assertEquals(false, prefs.getBoolean("show_illustrations", true))
        assertEquals("LANDSCAPE", prefs.getString("orientation_lock", null))
    }

    @Test
    fun `the two split ratios are independent`() {
        manager = newManager()

        manager.updateAppearance { it.copy(verticalSplitRatio = 0.65f) }

        assertEquals(
            "updateAppearance wrote the vertical ratio into the horizontal ratio's key.",
            ReadingDefaults.SPLIT_RATIO, prefs.getFloat("split_ratio", ReadingDefaults.SPLIT_RATIO), 1e-6f,
        )
        assertEquals(0.65f, prefs.getFloat("vertical_split_ratio", -1f), 1e-6f)
    }

    @Test
    fun `updateAppearance clamps`() {
        manager = newManager()

        manager.updateAppearance { it.copy(horizontalMargin = 999f) }

        val max = ReadingDefaults.HORIZONTAL_MARGIN_RANGE.endInclusive
        assertEquals(max, manager.appearance.value.horizontalMargin, 1e-6f)
        assertEquals(max, prefs.getFloat("horizontal_margin", -1f), 1e-6f)
    }

    @Test
    fun `an old setter keeps appearance in sync`() {
        manager = newManager()

        manager.saveTextSize(18f)

        assertEquals(18f, manager.appearance.value.textSize, 1e-6f)
    }

    @Test
    fun `updateAppearance keeps the readerThemeName flow in sync`() {
        manager = newManager()

        manager.updateAppearance { it.copy(theme = ReaderThemeKey.NIGHT) }

        assertEquals("NIGHT", manager.readerThemeName.value)
    }
}
