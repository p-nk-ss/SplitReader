package com.example.splitreader.presentation.theme

import androidx.compose.ui.graphics.Color

// Editorial paper palette. Hex values are sRGB approximations of the oklch
// values used in the design mock — see design_handoff_splitreader/README.md
// for the full token table. Prefer these constants over raw hex anywhere
// in the UI.

// ── Paper (default light) ───────────────────────────────────────────────
val PaperBg        = Color(0xFFF5EFE3)
val PaperBg2       = Color(0xFFEFE7D7)
val PaperBg3       = Color(0xFFE5DAC4)
val PaperEdge      = Color(0xFFD2C5AB)
val PaperRule      = Color(0xFFBBAB8E)
val PaperInk       = Color(0xFF2A2218)
val PaperInk2      = Color(0xFF544531)
val PaperInk3      = Color(0xFF6B5C44) // AA-safe small-label ink on PaperBg (≈4.7:1)
val PaperInk4      = Color(0xFFB3A790) // decorative only (dividers, ticks) — never readable text
val PaperAccent    = Color(0xFFB85D2D) // terracotta
val PaperAccentSoft= Color(0xFFF0D2BB)
val PaperMoss      = Color(0xFF5B7A52)
val PaperMossSoft  = Color(0xFFD8E2C8)

// ── Sepia (warm parchment) ──────────────────────────────────────────────
val SepiaBg        = Color(0xFFEFE0C2)
val SepiaBg2       = Color(0xFFE9D7B3)
val SepiaBg3       = Color(0xFFDDC79A)
val SepiaEdge      = Color(0xFFC8AD7D)
val SepiaRule      = Color(0xFFB59B6A)
val SepiaInk       = Color(0xFF3A2A1B)
val SepiaInk2      = Color(0xFF624A30)
val SepiaInk3      = Color(0xFF6E5839) // AA-safe small-label ink on SepiaBg (≥4.5:1)
val SepiaInk4      = Color(0xFFAE957A) // decorative only — never readable text
val SepiaAccent    = Color(0xFFA34A1F)
val SepiaAccentSoft= Color(0xFFE5BFA3)
val SepiaMoss      = Color(0xFF587047)
val SepiaMossSoft  = Color(0xFFD0DCB5)

// ── Night (slate blue) ──────────────────────────────────────────────────
val NightBg        = Color(0xFF15171F)
val NightBg2       = Color(0xFF1B1E28)
val NightBg3       = Color(0xFF272A36)
val NightEdge      = Color(0xFF323545)
val NightRule      = Color(0xFF323545)
val NightInk       = Color(0xFFEEEAE0)
val NightInk2      = Color(0xFFC8C2B4)
val NightInk3      = Color(0xFF969080)
val NightInk4      = Color(0xFF646055)
val NightAccent    = Color(0xFFE08B5A)
val NightAccentSoft= Color(0xFF50321E)
val NightMoss      = Color(0xFFA1C397)
val NightMossSoft  = Color(0xFF3A4A36)

// ── Semantic (cross-palette) ────────────────────────────────────────────
// Muted brick red for destructive/error UI. Deliberately distinct from the
// terracotta accent so an error never reads as a normal accent.
val ErrorTone      = Color(0xFF9E3B2A)

// ── AMOLED (true black) ─────────────────────────────────────────────────
val AmoledBg        = Color(0xFF000000)
val AmoledBg2       = Color(0xFF111111)
val AmoledBg3       = Color(0xFF1D1D1D)
val AmoledEdge      = Color(0xFF2A2A2A)
val AmoledRule      = Color(0xFF2A2A2A)
val AmoledInk       = Color(0xFFE8E4DC)
val AmoledInk2      = Color(0xFFBFBAAF)
val AmoledInk3      = Color(0xFF8E897E)
val AmoledInk4      = Color(0xFF5A5750)
val AmoledAccent    = Color(0xFFE08F5C)
val AmoledAccentSoft= Color(0xFF3A2418)
val AmoledMoss      = Color(0xFFA1C397)
val AmoledMossSoft  = Color(0xFF1E2C1A)
