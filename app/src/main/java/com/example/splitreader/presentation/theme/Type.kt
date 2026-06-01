package com.example.splitreader.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.splitreader.R

// Google Fonts integration. Requires:
//   implementation("androidx.compose.ui:ui-text-google-fonts") in build.gradle.kts
//   res/values/font_certs.xml with the GMS fonts certs array

private val gfProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val newsreaderGF = GoogleFont("Newsreader")
private val jetbrainsMonoGF = GoogleFont("JetBrains Mono")

val Newsreader = FontFamily(
    Font(googleFont = newsreaderGF, fontProvider = gfProvider, weight = FontWeight.Light),
    Font(googleFont = newsreaderGF, fontProvider = gfProvider, weight = FontWeight.Normal),
    Font(googleFont = newsreaderGF, fontProvider = gfProvider, weight = FontWeight.Medium),
    Font(googleFont = newsreaderGF, fontProvider = gfProvider, weight = FontWeight.SemiBold),
    Font(googleFont = newsreaderGF, fontProvider = gfProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = newsreaderGF, fontProvider = gfProvider, weight = FontWeight.Medium, style = FontStyle.Italic),
)

val JetBrainsMono = FontFamily(
    Font(googleFont = jetbrainsMonoGF, fontProvider = gfProvider, weight = FontWeight.Normal),
    Font(googleFont = jetbrainsMonoGF, fontProvider = gfProvider, weight = FontWeight.Medium),
    Font(googleFont = jetbrainsMonoGF, fontProvider = gfProvider, weight = FontWeight.SemiBold),
)

/**
 * User-selectable typeface for the reading panes (original + translation).
 *
 * SERIF keeps the app's Newsreader serif (the default look). SANS and MONO map to the platform's
 * built-in families so the choice always renders distinctly, with no dependency on a downloadable
 * Google Font being available on the device.
 */
enum class ReadingFont(val displayName: String) {
    SERIF("Serif"),
    SANS("Sans"),
    MONO("Mono");

    val fontFamily: FontFamily
        get() = when (this) {
            SERIF -> Newsreader
            SANS -> FontFamily.SansSerif
            MONO -> FontFamily.Monospace
        }
}

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
