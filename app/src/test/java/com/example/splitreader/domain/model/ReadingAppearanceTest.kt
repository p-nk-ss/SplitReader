package com.example.splitreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingAppearanceTest {

    private val base = ReadingAppearance.defaults(isTablet = false)

    @Test
    fun `sanitized clamps every number into its ReadingDefaults range`() {
        val low = base.copy(
            textSize = -5f, lineHeight = 0f, letterSpacing = -1f, textIndent = -3f,
            paragraphSpacing = -1f, splitRatio = 0f, verticalSplitRatio = 0f, horizontalMargin = 0f,
        ).sanitized()
        assertEquals(ReadingDefaults.TEXT_SIZE_RANGE.start, low.textSize, 0f)
        assertEquals(ReadingDefaults.LINE_HEIGHT_RANGE.start, low.lineHeight, 0f)
        assertEquals(ReadingDefaults.LETTER_SPACING_RANGE.start, low.letterSpacing, 0f)
        assertEquals(ReadingDefaults.TEXT_INDENT_RANGE.start, low.textIndent, 0f)
        assertEquals(ReadingDefaults.PARAGRAPH_SPACING_RANGE.start, low.paragraphSpacing, 0f)
        assertEquals(ReadingDefaults.SPLIT_RATIO_RANGE.start, low.splitRatio, 0f)
        assertEquals(ReadingDefaults.VERTICAL_SPLIT_RATIO_RANGE.start, low.verticalSplitRatio, 0f)
        assertEquals(ReadingDefaults.HORIZONTAL_MARGIN_RANGE.start, low.horizontalMargin, 0f)

        val high = base.copy(
            textSize = 999f, lineHeight = 99f, letterSpacing = 99f, textIndent = 999f,
            paragraphSpacing = 999f, splitRatio = 1f, verticalSplitRatio = 1f, horizontalMargin = 999f,
        ).sanitized()
        assertEquals(ReadingDefaults.TEXT_SIZE_RANGE.endInclusive, high.textSize, 0f)
        assertEquals(ReadingDefaults.LINE_HEIGHT_RANGE.endInclusive, high.lineHeight, 0f)
        assertEquals(ReadingDefaults.LETTER_SPACING_RANGE.endInclusive, high.letterSpacing, 0f)
        assertEquals(ReadingDefaults.TEXT_INDENT_RANGE.endInclusive, high.textIndent, 0f)
        assertEquals(ReadingDefaults.PARAGRAPH_SPACING_RANGE.endInclusive, high.paragraphSpacing, 0f)
        assertEquals(ReadingDefaults.SPLIT_RATIO_RANGE.endInclusive, high.splitRatio, 0f)
        assertEquals(ReadingDefaults.VERTICAL_SPLIT_RATIO_RANGE.endInclusive, high.verticalSplitRatio, 0f)
        assertEquals(ReadingDefaults.HORIZONTAL_MARGIN_RANGE.endInclusive, high.horizontalMargin, 0f)
    }

    @Test
    fun `in-range values are untouched`() {
        assertEquals(base, base.sanitized())
    }

    @Test
    fun `theme names migrate DEFAULT and unknowns to PAPER`() {
        assertEquals(ReaderThemeKey.PAPER, ReadingAppearance.themeFromName("DEFAULT"))
        assertEquals(ReaderThemeKey.PAPER, ReadingAppearance.themeFromName("nonsense"))
        assertEquals(ReaderThemeKey.PAPER, ReadingAppearance.themeFromName(null))
        assertEquals(ReaderThemeKey.NIGHT, ReadingAppearance.themeFromName("NIGHT"))
    }

    @Test
    fun `unknown font names fall back to the default font`() {
        assertEquals(ReadingAppearance.fontFromName(ReadingDefaults.READING_FONT), ReadingAppearance.fontFromName("nope"))
        assertEquals(ReadingFont.MONO, ReadingAppearance.fontFromName("MONO"))
    }

    @Test
    fun `defaults follow ReadingDefaults, orientation by form factor`() {
        assertEquals(ReadingDefaults.HORIZONTAL_MARGIN, base.horizontalMargin, 0f)
        assertEquals(defaultOrientationLock(false), base.orientationLock)
        assertEquals(defaultOrientationLock(true), ReadingAppearance.defaults(isTablet = true).orientationLock)
    }
}
