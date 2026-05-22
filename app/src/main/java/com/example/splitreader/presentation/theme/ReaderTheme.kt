package com.example.splitreader.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Reading-pane palette. The user picks one of these in the Display sheet.
// Only the reader content area swaps palettes; the side rail, library, etc.
// stay on the default Paper palette.
@Immutable
data class ReaderPalette(
    val key: ReaderThemeKey,
    val displayName: String,
    val bg: Color,
    val bg2: Color,
    val bg3: Color,
    val edge: Color,
    val rule: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val accent: Color,
    val accentSoft: Color,
    val moss: Color,
    val mossSoft: Color,
    val isDark: Boolean,
)

enum class ReaderThemeKey { PAPER, SEPIA, NIGHT, AMOLED }

val PaperPalette = ReaderPalette(
    key = ReaderThemeKey.PAPER, displayName = "Paper",
    bg = PaperBg, bg2 = PaperBg2, bg3 = PaperBg3,
    edge = PaperEdge, rule = PaperRule,
    ink = PaperInk, ink2 = PaperInk2, ink3 = PaperInk3, ink4 = PaperInk4,
    accent = PaperAccent, accentSoft = PaperAccentSoft,
    moss = PaperMoss, mossSoft = PaperMossSoft,
    isDark = false,
)

val SepiaPalette = ReaderPalette(
    key = ReaderThemeKey.SEPIA, displayName = "Sepia",
    bg = SepiaBg, bg2 = SepiaBg2, bg3 = SepiaBg3,
    edge = SepiaEdge, rule = SepiaRule,
    ink = SepiaInk, ink2 = SepiaInk2, ink3 = SepiaInk3, ink4 = SepiaInk4,
    accent = SepiaAccent, accentSoft = SepiaAccentSoft,
    moss = SepiaMoss, mossSoft = SepiaMossSoft,
    isDark = false,
)

val NightPalette = ReaderPalette(
    key = ReaderThemeKey.NIGHT, displayName = "Night",
    bg = NightBg, bg2 = NightBg2, bg3 = NightBg3,
    edge = NightEdge, rule = NightRule,
    ink = NightInk, ink2 = NightInk2, ink3 = NightInk3, ink4 = NightInk4,
    accent = NightAccent, accentSoft = NightAccentSoft,
    moss = NightMoss, mossSoft = NightMossSoft,
    isDark = true,
)

val AmoledPalette = ReaderPalette(
    key = ReaderThemeKey.AMOLED, displayName = "AMOLED",
    bg = AmoledBg, bg2 = AmoledBg2, bg3 = AmoledBg3,
    edge = AmoledEdge, rule = AmoledRule,
    ink = AmoledInk, ink2 = AmoledInk2, ink3 = AmoledInk3, ink4 = AmoledInk4,
    accent = AmoledAccent, accentSoft = AmoledAccentSoft,
    moss = AmoledMoss, mossSoft = AmoledMossSoft,
    isDark = true,
)

fun readerPalette(key: ReaderThemeKey): ReaderPalette = when (key) {
    ReaderThemeKey.PAPER  -> PaperPalette
    ReaderThemeKey.SEPIA  -> SepiaPalette
    ReaderThemeKey.NIGHT  -> NightPalette
    ReaderThemeKey.AMOLED -> AmoledPalette
}

// Use LocalReaderPalette.current inside the Reader screen and its children.
val LocalReaderPalette = staticCompositionLocalOf { PaperPalette }
