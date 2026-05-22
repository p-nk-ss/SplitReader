package com.example.splitreader.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Outer-chrome ColorScheme (Library, NavRail, Almanac, Vocabulaire).
// The Reader uses LocalReaderPalette separately and is decoupled from this.
private val SplitReaderColorScheme = lightColorScheme(
    primary             = PaperAccent,
    onPrimary           = PaperBg,
    primaryContainer    = PaperAccentSoft,
    onPrimaryContainer  = PaperInk,
    secondary           = PaperInk2,
    onSecondary         = PaperBg,
    background          = PaperBg,
    onBackground        = PaperInk,
    surface             = PaperBg,
    onSurface           = PaperInk,
    surfaceVariant      = PaperBg2,
    onSurfaceVariant    = PaperInk2,
    surfaceTint         = PaperAccent,
    outline             = PaperEdge,
    outlineVariant      = PaperRule,
    inverseSurface      = PaperInk,
    inverseOnSurface    = PaperBg,
    error               = PaperAccent,
)

@Composable
fun SplitReaderTheme(
    readerThemeKey: ReaderThemeKey = ReaderThemeKey.PAPER,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing       provides Spacing(),
        LocalRadii         provides Radii(),
        LocalReaderPalette provides readerPalette(readerThemeKey),
    ) {
        MaterialTheme(
            colorScheme = SplitReaderColorScheme,
            typography  = SplitReaderTypography,
            content     = content,
        )
    }
}
