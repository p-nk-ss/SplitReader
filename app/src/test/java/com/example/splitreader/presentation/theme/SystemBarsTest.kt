package com.example.splitreader.presentation.theme

import android.app.Application
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.example.splitreader.domain.model.ReaderThemeKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * System bar icons follow the reader palette, not the system's dark mode: a Night palette on a light
 * system needs light icons, and Paper on a dark system needs dark ones. Edge-to-edge stays on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SystemBarsTest {

    private fun activity() = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private fun ComponentActivity.lightIcons(): Pair<Boolean, Boolean> {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        return c.isAppearanceLightStatusBars to c.isAppearanceLightNavigationBars
    }

    @Test
    @Config(qualifiers = "notnight")
    fun darkPaletteOnLightSystemGetsLightIcons() {
        val a = activity()
        a.applySystemBars(isDark = readerPalette(ReaderThemeKey.NIGHT).isDark)
        assertEquals(false to false, a.lightIcons())
    }

    @Test
    @Config(qualifiers = "night")
    fun lightPaletteOnDarkSystemGetsDarkIcons() {
        val a = activity()
        assertEquals(Configuration.UI_MODE_NIGHT_YES, a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        a.applySystemBars(isDark = readerPalette(ReaderThemeKey.PAPER).isDark)
        assertEquals(true to true, a.lightIcons())
    }

    @Test
    @Config(qualifiers = "notnight")
    fun switchingPaletteFlipsIcons() {
        val a = activity()
        a.applySystemBars(isDark = false)
        assertEquals(true to true, a.lightIcons())
        a.applySystemBars(isDark = true)
        assertEquals(false to false, a.lightIcons())
    }
}
