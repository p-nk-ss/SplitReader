package com.example.splitreader.presentation.almanac

import org.junit.Assert.assertEquals
import org.junit.Test

/** Almanac's language badges (2026-11-04: a book with no detected language showed "UND"). */
class LanguageBadgeTest {
    @Test
    fun `a book with no detected language is Unknown, not UND`() {
        assertEquals("UNKNOWN", languageBadge("und"))
        assertEquals("UNKNOWN", languageBadge(""))
    }

    @Test
    fun `a language is its code, without the script`() {
        assertEquals("DE", languageBadge("de"))
        assertEquals("HI", languageBadge("hi-latn"))
        assertEquals("FIL", languageBadge("fil"))
    }
}
