package com.example.splitreader.domain.model

/**
 * The user-selectable reading-pane palette. Moved from `presentation.theme` so the domain layer
 * (persistence defaults, [ReadingAppearance]) does not depend on presentation. The Compose palette
 * mapping (`readerPalette`) stays in `presentation.theme.ReaderTheme`.
 */
enum class ReaderThemeKey { PAPER, SEPIA, NIGHT, AMOLED }
