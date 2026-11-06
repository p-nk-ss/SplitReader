package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphLanguageTest {

    private class Confident(val answer: String?) : LanguageDetector {
        val asked = mutableListOf<String>()
        override suspend fun identify(text: String): String? = error("the book-level call")
        override suspend fun identifyConfident(text: String): String? = answer.also { asked += text }
    }

    private val long = "Ein ganzer Absatz in einer anderen Sprache, lang genug für eine sichere Erkennung."

    private suspend fun decide(answer: String?, text: String = long) =
        ParagraphLanguage(Confident(answer)).decide(text, book = Language.ENGLISH, target = Language.RUSSIAN)

    @Test
    fun `a paragraph in the book's language is the book's`() = runTest {
        assertEquals(ParagraphLanguage.Decision.Book, decide("en"))
    }

    @Test
    fun `a paragraph in another supported language is translated from it`() = runTest {
        assertEquals(ParagraphLanguage.Decision.Other(Language.GERMAN), decide("de"))
    }

    @Test
    fun `a paragraph already in the target language is kept`() = runTest {
        assertEquals(ParagraphLanguage.Decision.Keep("ru"), decide("ru"))
    }

    @Test
    fun `a paragraph in an unsupported language is kept`() = runTest {
        assertEquals(ParagraphLanguage.Decision.Keep("la"), decide("la"))
    }

    @Test
    fun `an unsure answer leaves the paragraph to the book's language`() = runTest {
        assertEquals(ParagraphLanguage.Decision.Book, decide(null))
    }

    @Test
    fun `short lines are never rerouted, and the detector isn't asked`() = runTest {
        val detector = Confident("de")
        val d = ParagraphLanguage(detector).decide("Ja, natürlich!", book = Language.ENGLISH, target = Language.RUSSIAN)
        assertEquals(ParagraphLanguage.Decision.Book, d)
        assertTrue(detector.asked.isEmpty())
    }

    @Test
    fun `a paragraph in what the whole book was detected as is the book's, when the reader chose another language`() = runTest {
        // A Belarusian book the reader reads as Russian: its paragraphs detect as "be", and must
        // not each show "BE · not translated".
        val d = ParagraphLanguage(Confident("be")).decide(long, book = Language.RUSSIAN, target = Language.ENGLISH, bookDetectedAs = "be")
        assertEquals(ParagraphLanguage.Decision.Book, d)
    }

    @Test
    fun `the detector is asked once per text, with at most the first thousand characters`() = runTest {
        val detector = Confident("de")
        val pl = ParagraphLanguage(detector)
        val chapter = long.repeat(40)
        pl.decide(chapter, book = Language.ENGLISH, target = Language.RUSSIAN)
        pl.decide(chapter, book = Language.ENGLISH, target = Language.RUSSIAN)
        assertEquals(1, detector.asked.size)
        assertTrue(detector.asked.single().length <= 1000)
    }

    // ── CJK (2026-11-06): 49 Chinese characters are a long sentence, not a "short line" ──
    // In an English book they went to EN→RU as if English and came out as garbage.

    @Test
    fun `a Chinese sentence of fifty characters is told apart, though under sixty characters`() = runTest {
        val zh = "此開卷第一回也。作者自云：因曾歷過一番夢幻之後，故將真事隱去，而借通靈之說，撰此《石頭記》一書也。"
        assertEquals(ParagraphLanguage.Decision.Other(Language.CHINESE), ParagraphLanguage(Confident("zh")).decide(zh, Language.ENGLISH, Language.RUSSIAN))
    }

    @Test
    fun `a Japanese sentence is told apart too, but a two-word reply still isn't`() = runTest {
        val ja = "或日の暮方の事である。一人の下人が、羅生門の下で雨やみを待っていた。"
        assertEquals(ParagraphLanguage.Decision.Other(Language.JAPANESE), ParagraphLanguage(Confident("ja")).decide(ja, Language.ENGLISH, Language.RUSSIAN))
        val detector = Confident("ja")
        assertEquals(ParagraphLanguage.Decision.Book, ParagraphLanguage(detector).decide("はい、そうです。", Language.ENGLISH, Language.RUSSIAN))
        assertTrue(detector.asked.isEmpty())
    }

    @Test
    fun `twenty Han characters reach the bar, nineteen don't`() = runTest {
        val nineteen = Confident("zh")
        assertEquals(ParagraphLanguage.Decision.Book, ParagraphLanguage(nineteen).decide("天".repeat(19), Language.ENGLISH, Language.RUSSIAN))
        assertTrue(nineteen.asked.isEmpty())
        val twenty = Confident("zh")
        ParagraphLanguage(twenty).decide("天".repeat(20), Language.ENGLISH, Language.RUSSIAN)
        assertEquals(1, twenty.asked.size)
    }

    @Test
    fun `a character outside the BMP counts once, and CJK punctuation as a letter`() {
        assertEquals(3, ParagraphLanguage.weightedLength("𠀀"))
        assertEquals(5, ParagraphLanguage.weightedLength("。、「」ー"))
    }

    @Test
    fun `romanised text of a non-Latin language is kept, not translated as that language`() = runTest {
        assertEquals(ParagraphLanguage.Decision.Keep("hi-latn"), decide("hi-Latn"))
    }
}
