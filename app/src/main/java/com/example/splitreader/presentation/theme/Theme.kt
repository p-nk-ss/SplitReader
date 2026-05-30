package com.example.splitreader.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Outer-chrome ColorScheme (Library, NavRail, Almanac, Words). It is now driven
// by the active reader palette, so selecting a dark reader theme (Night/AMOLED)
// darkens the whole app — not just the reading pane. The reader still consumes
// LocalReaderPalette for fine control.
@Composable
fun SplitReaderTheme(
    readerThemeKey: ReaderThemeKey = ReaderThemeKey.PAPER,
    content: @Composable () -> Unit,
) {
    val palette = readerPalette(readerThemeKey)
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary             = palette.accent,
            onPrimary           = palette.bg,
            primaryContainer    = palette.accentSoft,
            onPrimaryContainer  = palette.ink,
            secondary           = palette.ink2,
            onSecondary         = palette.bg,
            background          = palette.bg,
            onBackground        = palette.ink,
            surface             = palette.bg,
            onSurface           = palette.ink,
            surfaceVariant      = palette.bg2,
            onSurfaceVariant    = palette.ink2,
            surfaceTint         = palette.accent,
            outline             = palette.edge,
            outlineVariant      = palette.rule,
            inverseSurface      = palette.ink,
            inverseOnSurface    = palette.bg,
            error               = ErrorTone,
        )
    } else {
        lightColorScheme(
            primary             = palette.accent,
            onPrimary           = palette.bg,
            primaryContainer    = palette.accentSoft,
            onPrimaryContainer  = palette.ink,
            secondary           = palette.ink2,
            onSecondary         = palette.bg,
            background          = palette.bg,
            onBackground        = palette.ink,
            surface             = palette.bg,
            onSurface           = palette.ink,
            surfaceVariant      = palette.bg2,
            onSurfaceVariant    = palette.ink2,
            surfaceTint         = palette.accent,
            outline             = palette.edge,
            outlineVariant      = palette.rule,
            inverseSurface      = palette.ink,
            inverseOnSurface    = palette.bg,
            error               = ErrorTone,
        )
    }
    CompositionLocalProvider(
        LocalSpacing       provides Spacing(),
        LocalRadii         provides Radii(),
        LocalReaderPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography  = SplitReaderTypography,
            content     = content,
        )
    }
}
