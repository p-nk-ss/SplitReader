package com.example.splitreader.presentation.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.splitreader.R

// Reading typefaces bundled as app resources (res/font, OFL-licensed). Bundling — rather than the
// downloadable Google Fonts used for the app chrome — guarantees these render on any device or
// emulator, with no Play Services or network dependency. Each is loaded from a single (variable or
// regular) .ttf; Compose synthesizes heavier weights where needed.

private val Literata = FontFamily(Font(R.font.literata))
private val Lora = FontFamily(Font(R.font.lora))
private val Merriweather = FontFamily(Font(R.font.merriweather))
private val EbGaramond = FontFamily(Font(R.font.ebgaramond))
private val Inter = FontFamily(Font(R.font.inter))
private val Lexend = FontFamily(Font(R.font.lexend))
private val AtkinsonHyperlegible = FontFamily(Font(R.font.atkinson))
private val FiraCode = FontFamily(Font(R.font.firacode))

/**
 * User-selectable typeface for the reading panes (original + translation).
 *
 * The first three map to the platform's built-in families (always available); the rest are
 * bundled OFL fonts. The chip for each option renders its own label in that typeface as a preview.
 */
enum class ReadingFont(val displayName: String) {
    SERIF("Serif"),
    SANS("Sans"),
    MONO("Mono"),
    LITERATA("Literata"),
    LORA("Lora"),
    MERRIWEATHER("Merriweather"),
    EB_GARAMOND("Garamond"),
    INTER("Inter"),
    LEXEND("Lexend"),
    ATKINSON("Atkinson"),
    FIRA_CODE("Fira Code");

    val fontFamily: FontFamily
        get() = when (this) {
            SERIF -> FontFamily.Serif
            SANS -> FontFamily.SansSerif
            MONO -> FontFamily.Monospace
            LITERATA -> Literata
            LORA -> Lora
            MERRIWEATHER -> Merriweather
            EB_GARAMOND -> EbGaramond
            INTER -> Inter
            LEXEND -> Lexend
            ATKINSON -> AtkinsonHyperlegible
            FIRA_CODE -> FiraCode
        }
}
