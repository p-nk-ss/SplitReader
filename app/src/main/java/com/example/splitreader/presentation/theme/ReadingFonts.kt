@file:OptIn(ExperimentalTextApi::class)

package com.example.splitreader.presentation.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.splitreader.R
import com.example.splitreader.domain.model.ReadingFont

// The app's editorial serif, bundled as a variable font (res/font/newsreader*.ttf). Each declared
// weight points at the variable resource with the matching wght axis so the chrome keeps its
// Light/Medium/SemiBold contrast without depending on downloadable Google Fonts.
private fun newsreaderUpright(resId: Int, weight: FontWeight, axis: Int) =
    Font(resId, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

val Newsreader = FontFamily(
    newsreaderUpright(R.font.newsreader, FontWeight.Light, 300),
    newsreaderUpright(R.font.newsreader, FontWeight.Normal, 400),
    newsreaderUpright(R.font.newsreader, FontWeight.Medium, 500),
    newsreaderUpright(R.font.newsreader, FontWeight.SemiBold, 600),
    Font(
        R.font.newsreader_italic,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.newsreader_italic,
        weight = FontWeight.Medium,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

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
 * Compose typeface for a [ReadingFont]. The enum itself moved to `domain.model.ReadingFont` (pure,
 * no Compose); this mapping is the presentation-side counterpart. The chip for each option renders
 * its own label in that typeface as a preview.
 */
val ReadingFont.fontFamily: FontFamily
    get() = when (this) {
        ReadingFont.SERIF -> FontFamily.Serif
        ReadingFont.SANS -> FontFamily.SansSerif
        ReadingFont.MONO -> FontFamily.Monospace
        ReadingFont.NEWSREADER -> Newsreader
        ReadingFont.LITERATA -> Literata
        ReadingFont.LORA -> Lora
        ReadingFont.MERRIWEATHER -> Merriweather
        ReadingFont.EB_GARAMOND -> EbGaramond
        ReadingFont.INTER -> Inter
        ReadingFont.LEXEND -> Lexend
        ReadingFont.ATKINSON -> AtkinsonHyperlegible
        ReadingFont.FIRA_CODE -> FiraCode
    }
