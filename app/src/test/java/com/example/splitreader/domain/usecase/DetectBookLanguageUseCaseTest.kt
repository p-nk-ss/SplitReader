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

    @Test
    fun `a script-qualified code counts as its language`() = runTest {
        val result = DetectBookLanguageUseCase(Scripted { "zh-Latn" })(book(prose("p", 20)))
        assertEquals(SourceDetection.Supported(Language.CHINESE), result)
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
        val firsts = samples.map { it.substringBefore(' ').removePrefix("para").toInt() }
        assertTrue("first sample starts in the skipped front matter: $firsts", firsts.first() >= 10)
        assertTrue("samples don't reach the last third: $firsts", firsts.last() >= 140)
    }
}
