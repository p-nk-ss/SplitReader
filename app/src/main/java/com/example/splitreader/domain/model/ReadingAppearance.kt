package com.example.splitreader.domain.model

/**
 * Everything that controls how the reading panes look: theme, typography and layout, plus the two
 * reader-wide toggles (translation, illustrations) and orientation. Deliberately excludes the
 * portrait hint, TTS, target language and reading progress — those live elsewhere.
 *
 * This is the single value the `ReadingPreferences` port stores and publishes (see
 * `docs/superpowers/specs/2026-10-12-reading-appearance-design.md` §3.1); [sanitized] is the only
 * place bounds are applied, so a value written to prefs outside the normal setters still comes back
 * clamped on read.
 */
data class ReadingAppearance(
    val theme: ReaderThemeKey,
    val font: ReadingFont,
    val textSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val textIndent: Float,
    val paragraphSpacing: Float,
    val justify: Boolean,
    val splitRatio: Float,
    val verticalSplitRatio: Float,
    val horizontalMargin: Float,
    val showTranslation: Boolean,
    val showIllustrations: Boolean,
    val orientationLock: OrientationLock,
) {
    /** Every number clamped into its [ReadingDefaults] range. The only place bounds are applied. */
    fun sanitized(): ReadingAppearance = copy(
        textSize = textSize.coerceIn(ReadingDefaults.TEXT_SIZE_RANGE),
        lineHeight = lineHeight.coerceIn(ReadingDefaults.LINE_HEIGHT_RANGE),
        letterSpacing = letterSpacing.coerceIn(ReadingDefaults.LETTER_SPACING_RANGE),
        textIndent = textIndent.coerceIn(ReadingDefaults.TEXT_INDENT_RANGE),
        paragraphSpacing = paragraphSpacing.coerceIn(ReadingDefaults.PARAGRAPH_SPACING_RANGE),
        splitRatio = splitRatio.coerceIn(ReadingDefaults.SPLIT_RATIO_RANGE),
        verticalSplitRatio = verticalSplitRatio.coerceIn(ReadingDefaults.VERTICAL_SPLIT_RATIO_RANGE),
        horizontalMargin = horizontalMargin.coerceIn(ReadingDefaults.HORIZONTAL_MARGIN_RANGE),
    )

    companion object {
        /** First-launch appearance: every field from [ReadingDefaults], theme PAPER, orientation by form factor. */
        fun defaults(isTablet: Boolean): ReadingAppearance = ReadingAppearance(
            theme = ReaderThemeKey.PAPER,
            font = fontFromName(ReadingDefaults.READING_FONT),
            textSize = ReadingDefaults.TEXT_SIZE,
            lineHeight = ReadingDefaults.LINE_HEIGHT,
            letterSpacing = ReadingDefaults.LETTER_SPACING,
            textIndent = ReadingDefaults.TEXT_INDENT,
            paragraphSpacing = ReadingDefaults.PARAGRAPH_SPACING,
            justify = ReadingDefaults.JUSTIFY_TEXT,
            splitRatio = ReadingDefaults.SPLIT_RATIO,
            verticalSplitRatio = ReadingDefaults.VERTICAL_SPLIT_RATIO,
            horizontalMargin = ReadingDefaults.HORIZONTAL_MARGIN,
            showTranslation = ReadingDefaults.SHOW_TRANSLATION,
            showIllustrations = ReadingDefaults.SHOW_ILLUSTRATIONS,
            orientationLock = defaultOrientationLock(isTablet),
        )

        /** Stored theme name -> key: "DEFAULT" and unknown names -> PAPER. The only theme migration. */
        fun themeFromName(name: String?): ReaderThemeKey =
            ReaderThemeKey.entries.firstOrNull { it.name == name } ?: ReaderThemeKey.PAPER

        /** Stored font name -> font: unknown -> default. */
        fun fontFromName(name: String?): ReadingFont =
            ReadingFont.entries.firstOrNull { it.name == name }
                ?: ReadingFont.entries.first { it.name == ReadingDefaults.READING_FONT }
    }
}
