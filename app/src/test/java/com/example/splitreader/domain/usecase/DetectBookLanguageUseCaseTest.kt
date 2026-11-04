package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SourceDetection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectBookLanguageUseCaseTest {

    private class Scripted(val answer: (String) -> String?) : LanguageDetector {
        val asked = mutableListOf<String>()
        override suspend fun identify(text: String): String? = answer(text).also { asked += text }
    }

    private fun book(paragraphs: List<String>) =
        Book(title = "T", author = "A", filePath = "/b", chapters = listOf(Chapter(index = 0, title = "C", paragraphs = paragraphs)))

    private fun prose(tag: String, n: Int) = List(n) { "$tag $it " + "x".repeat(60) }

    @Test
    fun `a book with no text is Unknown and the detector is never asked`() = runTest {
        val detector = Scripted { "en" }
        val result = DetectBookLanguageUseCase(detector)(book(listOf("", "   ", "\n")))
        assertEquals(SourceDetection.Unknown, result)
        assertTrue(detector.asked.isEmpty())
    }

    /** Verse and plays have short lines; a long-paragraph filter alone left them undetected and untranslated. */
    @Test
    fun `a book of short lines is still detected`() = runTest {
        val verse = List(120) { "Мой дядя самых честных правил $it" }
        val detector = Scripted { "ru" }
        assertEquals(SourceDetection.Supported(Language.RUSSIAN), DetectBookLanguageUseCase(detector)(book(verse)))
        assertTrue(detector.asked.isNotEmpty())
    }

    /** ML Kit tags romanised text ("hi-Latn"); the translators expect Hindi in Devanagari, so it isn't Hindi to them. */
    @Test
    fun `a romanised non-Latin language is Unsupported`() = runTest {
        val result = DetectBookLanguageUseCase(Scripted { "hi-Latn" })(book(prose("p", 20)))
        assertEquals(SourceDetection.Unsupported("hi-latn"), result)
    }

    @Test
    fun `a script tag on a Latin-script language still counts as the language`() = runTest {
        val result = DetectBookLanguageUseCase(Scripted { "de-Latn" })(book(prose("p", 20)))
        assertEquals(SourceDetection.Supported(Language.GERMAN), result)
    }

    @Test
    fun `a majority outside the supported languages is Unsupported with its code`() = runTest {
        val result = DetectBookLanguageUseCase(Scripted { if (it.startsWith("en")) "en" else "fi" })(book(prose("en", 5) + prose("fi", 95)))
        assertEquals(SourceDetection.Unsupported("fi"), result)
    }

    @Test
    fun `undetermined stretches don't vote`() = runTest {
        val result = DetectBookLanguageUseCase(Scripted { if (it.startsWith("q")) null else "de" })(book(prose("q", 60) + prose("de", 40)))
        assertEquals(SourceDetection.Supported(Language.GERMAN), result)
    }

    @Test
    fun `samples come from across the book, not its start`() {
        val samples = DetectBookLanguageUseCase.languageSamples(book(List(200) { "para$it " + "y".repeat(60) }))
        assertEquals(DetectBookLanguageUseCase.SAMPLES, samples.size)
        val firsts = samples.map { Regex("para(\\d+)").find(it)!!.groupValues[1].toInt() }
        assertTrue("first sample starts in the skipped front matter: $firsts", firsts.first() >= 10)
        assertTrue("samples don't reach the last third: $firsts", firsts.last() >= 140)
    }

    /**
     * Rashomon (Gutenberg #1982): two English notes, then the whole story as one long paragraph.
     * Spread over paragraphs the notes won two of three votes; spread over the text, the story wins.
     */
    @Test
    fun `one long paragraph outweighs a few short ones in another language`() = runTest {
        val notes = List(2) { "This file is encoded in Japanese and was taken from a 1917 edition, note $it. " + "z".repeat(200) }
        val story = "或日の暮方の事である。一人の下人が、羅生門の下で雨やみを待っていた。".repeat(150)
        // On the device ML Kit called a stretch of 271 English + 329 Japanese characters English; model
        // that: Japanese only when it clearly dominates.
        val detector = Scripted { s ->
            val cjk = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]").findAll(s).count()
            val latin = Regex("[A-Za-z]").findAll(s).count()
            if (cjk > 2 * latin) "ja" else "en"
        }
        assertEquals(SourceDetection.Supported(Language.JAPANESE), DetectBookLanguageUseCase(detector)(book(notes + story)))
    }
}
