package com.example.splitreader.domain.model

/**
 * User-selectable typeface for the reading panes (original + translation).
 *
 * Moved from `presentation.theme` so the domain layer (persistence defaults, [ReadingAppearance])
 * does not depend on presentation/Compose. The first three map to the platform's built-in families
 * (always available); the rest are bundled OFL fonts. The Compose `FontFamily` mapping lives in the
 * `ReadingFont.fontFamily` extension in `presentation.theme.ReadingFonts`.
 */
enum class ReadingFont(val displayName: String) {
    SERIF("Serif"),
    SANS("Sans"),
    MONO("Mono"),
    NEWSREADER("Newsreader"),
    LITERATA("Literata"),
    LORA("Lora"),
    MERRIWEATHER("Merriweather"),
    EB_GARAMOND("Garamond"),
    INTER("Inter"),
    LEXEND("Lexend"),
    ATKINSON("Atkinson"),
    FIRA_CODE("Fira Code"),
}
