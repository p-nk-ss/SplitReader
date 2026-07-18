package com.example.splitreader.domain.model

/**
 * Single source of truth for reader preference defaults and clamp ranges, so the persistence layer
 * ([data.local.ReadingProgressManager]) and the UI states cannot drift apart (they used to disagree
 * on the paragraph-spacing default and the text-size range).
 */
object ReadingDefaults {
    // Defaults
    const val LINE_HEIGHT = 1.5f
    const val SPLIT_RATIO = 0.5f
    const val SHOW_TRANSLATION = true
    const val SHOW_ILLUSTRATIONS = true
    const val HORIZONTAL_MARGIN = 12f
    const val TEXT_SIZE = 16f
    const val READING_FONT = "SERIF"
    const val PARAGRAPH_SPACING = 18f // was 8f in the persistence layer — 18f is the UI-designed value
    const val LETTER_SPACING = 0f
    const val TEXT_INDENT = 0f
    const val JUSTIFY_TEXT = true
    const val TTS_RATE = 1.0f
    const val TTS_PITCH = 1.0f
    const val READER_THEME = "DEFAULT"
    const val NAVIGATION_SIDE_LEFT = false

    // Clamp ranges (used by coerceIn and matching sliders)
    val TEXT_SIZE_RANGE = 14f..30f // unified (Settings used 14..24, Reader used 14..30)
    val SPLIT_RATIO_RANGE = 0.3f..0.7f
    val HORIZONTAL_MARGIN_RANGE = 4f..32f
    val LINE_HEIGHT_RANGE = 1.1f..2.5f
    val LETTER_SPACING_RANGE = 0f..2f
    val TEXT_INDENT_RANGE = 0f..48f
    val PARAGRAPH_SPACING_RANGE = 4f..48f
    val TTS_RATE_RANGE = 0.5f..2.0f
    val TTS_PITCH_RANGE = 0.5f..2.0f
}
