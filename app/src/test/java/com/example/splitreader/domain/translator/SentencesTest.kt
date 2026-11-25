package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencesTest {

    @Test
    fun `a long run without breaks is never cut inside a character outside the BMP`() {
        // 𠀀 (U+20000) is two UTF-16 units; 401 units of them would put a cut mid-pair at 400.
        val text = "一" + "𠀀".repeat(300)
        val pieces = Sentences.split(text, Language.CHINESE)
        assertEquals(text, pieces.joinToString(""))
        assertTrue("a piece starts or ends inside a surrogate pair", pieces.none { Character.isLowSurrogate(it.first()) || Character.isHighSurrogate(it.last()) })
    }

    @Test
    fun `Japanese sentences split on the full stop`() {
        assertEquals(listOf("一つ。", "二つ。"), Sentences.split("一つ。二つ。", Language.JAPANESE))
    }

    // ── Abbreviations (translation eval 2026-11-25: "миссис. Лонг говорит…") ─────────────────────
    private val english = Abbreviations.parse("# comment\n\nMr\nMrs\nNo #NUMERIC_ONLY#\n")

    @Test
    fun `a full stop after a known abbreviation does not end a sentence`() {
        assertEquals(
            listOf("Mrs. Long came in. ", "Mr. Morris left."),
            Sentences.split("Mrs. Long came in. Mr. Morris left.", Language.ENGLISH, abbreviations = english),
        )
    }

    @Test
    fun `a number-only abbreviation holds only before a number`() {
        assertEquals(
            listOf("Room No. 5 was empty. ", "He answered No. ", "Then he left."),
            Sentences.split("Room No. 5 was empty. He answered No. Then he left.", Language.ENGLISH, abbreviations = english),
        )
    }

    @Test
    fun `the list format skips comments and blank lines`() {
        assertEquals(setOf("Mr", "Mrs"), english.always)
        assertEquals(setOf("No"), english.beforeNumber)
    }

    /** A cut at the last space split "to take | possession" and the engine closed the first half with a full stop. */
    @Test
    fun `an overlong sentence is cut at a semicolon or comma before any plain space`() {
        val sentence = "He came down on Monday in a chaise and four to see the place, and was so much delighted " +
            "with it that he agreed with Mr. Morris immediately; that he is to take possession before Michaelmas, " +
            "and some of his servants are to be in the house by the end of next week."
        val pieces = Sentences.split(sentence, Language.ENGLISH, maxChars = 200, abbreviations = english)
        assertEquals(sentence, pieces.joinToString(""))
        assertTrue(pieces.toString(), pieces.dropLast(1).all { it.trimEnd().last() in ";," })
    }

    @Test
    fun `a long English paragraph needs splitting, a short one does not`() {
        assertTrue(Sentences.needsSplitting("a".repeat(Sentences.MAX_PIECE + 1), Language.ENGLISH))
        assertFalse(Sentences.needsSplitting("One. Two. Three.", Language.ENGLISH))
    }

    @Test
    fun `pieces join with spaces, or without into Chinese and Japanese`() {
        assertEquals("Один. Два.", Sentences.join(listOf("Один.", " Два. "), Language.RUSSIAN))
        assertEquals("一。二。", Sentences.join(listOf("一。", "二。"), Language.JAPANESE))
    }
}
