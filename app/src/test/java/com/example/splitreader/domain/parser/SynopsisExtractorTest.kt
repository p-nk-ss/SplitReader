package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SynopsisExtractorTest {

    @Test
    fun `normalize returns null for null or blank`() {
        assertNull(SynopsisExtractor.normalize(null))
        assertNull(SynopsisExtractor.normalize("   "))
    }

    @Test
    fun `normalize strips html and collapses whitespace`() {
        val raw = "<p>The story   of  <b>Emma</b>.</p>"
        assertEquals("The story of Emma.", SynopsisExtractor.normalize(raw))
    }

    @Test
    fun `normalize clamps long text and appends ellipsis`() {
        val result = SynopsisExtractor.normalize("a".repeat(400))!!
        assertEquals(281, result.length) // 280 chars + ellipsis
        assertEquals('…', result.last())
    }

    @Test
    fun `build prefers description over paragraph`() {
        val result = SynopsisExtractor.build(
            description = "A real description here.",
            paragraphs = listOf("This is the opening paragraph of the book body."),
        )
        assertEquals("A real description here.", result)
    }

    @Test
    fun `build falls back to first meaningful paragraph`() {
        val result = SynopsisExtractor.build(
            description = null,
            paragraphs = listOf("Short.", "This opening paragraph is clearly long enough to qualify."),
        )
        assertEquals("This opening paragraph is clearly long enough to qualify.", result)
    }

    @Test
    fun `build returns null when nothing qualifies`() {
        assertNull(SynopsisExtractor.build(null, listOf("tiny", "also")))
    }
}
