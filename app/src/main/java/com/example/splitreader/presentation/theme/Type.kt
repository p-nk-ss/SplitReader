@file:OptIn(ExperimentalTextApi::class)

package com.example.splitreader.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.splitreader.R

// JetBrains Mono (the app's monospace chrome face) is bundled as a variable-font res/font
// resource — same idiom as Newsreader (see ReadingFonts.kt) — so it renders reliably offline, on
// emulators without Google Play Services, and in JVM screenshot tests, with no visual change from
// the previously downloadable Google Font.
private fun jetbrainsMono(weight: FontWeight, axis: Int) =
    Font(R.font.jetbrainsmono, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

val JetBrainsMono = FontFamily(
    jetbrainsMono(FontWeight.Normal, 400),
    jetbrainsMono(FontWeight.Medium, 500),
    jetbrainsMono(FontWeight.SemiBold, 600),
)

// Material 3 Typography slots mapped to the editorial palette.
// • display / headline / title  → Newsreader serif
// • body                        → Newsreader, longer line-height for reading
// • label                       → JetBrains Mono uppercase for chrome ("eyebrow")
val SplitReaderTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    // Label — JetBrains Mono uppercase for chrome eyebrows / chapter codes /
    // page numbers / percentages. Always uppercase at the call site.
    // Label sizes respect the 11sp legibility floor (smallest readable label).
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
)

// Italic body — convenience for the many places that mix italic into serif.
val BodyItalic = TextStyle(
    fontFamily = Newsreader, fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
)
