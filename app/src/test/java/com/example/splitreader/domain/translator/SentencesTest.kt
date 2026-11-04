package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language
import org.junit.Assert.assertEquals
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

    @Test
    fun `pieces join with spaces, or without into Chinese and Japanese`() {
        assertEquals("Один. Два.", Sentences.join(listOf("Один.", " Два. "), Language.RUSSIAN))
        assertEquals("一。二。", Sentences.join(listOf("一。", "二。"), Language.JAPANESE))
    }
}
