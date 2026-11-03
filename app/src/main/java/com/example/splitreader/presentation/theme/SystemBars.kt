package com.example.splitreader.presentation.theme

import android.content.res.Resources
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge with system bar icons that follow the reader palette, not the system's dark mode:
 * the whole app takes the palette's colours (see [SplitReaderTheme]), so the light Paper palette
 * needs dark icons even on a dark system. Call again whenever the palette changes.
 *
 * The status bar is transparent. The navigation bar keeps the platform's contrast protection in
 * three-button mode (a scrim below API 29, enforced contrast above), because content of the other
 * tone can scroll under it: a book illustration, or the stacked pane while the reader bars are hidden.
 */
fun ComponentActivity.applySystemBars(isDark: Boolean) {
    val detectDark = { _: Resources -> isDark }
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, detectDark),
        navigationBarStyle = SystemBarStyle.auto(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM, detectDark),
    )
}

// The scrims androidx's own enableEdgeToEdge() defaults use.
private val NAV_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
