package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.ModelPair
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mozilla models all touch English, so every non-English pair must pivot — and nothing else may. */
class BergamotRoutesTest {
    @Test
    fun `same language needs no model`() {
        assertEquals(emptyList<ModelPair>(), routeFor(Language.RUSSIAN, Language.RUSSIAN))
    }

    @Test
    fun `pairs touching English are direct, all others pivot through English`() {
        for (s in Language.entries) for (t in Language.entries) {
            if (s == t) continue
            val route = routeFor(s, t)
            val expected = if (s == Language.ENGLISH || t == Language.ENGLISH) {
                listOf(ModelPair(s, t))
            } else {
                listOf(ModelPair(s, Language.ENGLISH), ModelPair(Language.ENGLISH, t))
            }
            assertEquals("${s.code}→${t.code}", expected, route)
        }
    }
}
