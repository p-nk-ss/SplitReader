package com.example.splitreader.presentation.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge with transparent system bars whose icons follow the reader palette, not the system's
 * dark mode: the whole app takes the palette's colours (see [SplitReaderTheme]), so a Night palette
 * on a light system needs light icons. Call again whenever the palette changes.
 *
 * Every screen pads the bar insets itself, so no scrim is needed in either navigation mode.
 */
fun ComponentActivity.applySystemBars(isDark: Boolean) {
    val style = if (isDark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
