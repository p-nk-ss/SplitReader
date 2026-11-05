package com.example.splitreader.presentation.reader

import android.app.Application
import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ParagraphNote
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeTranslationRepository
import com.example.splitreader.fakes.FakeTranslatorSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Download and translate" on a paragraph in another language (2026-11-04): Offline HQ has no
 * German pack, so the German paragraph in an English book shows as it is with a button; the button
 * downloads the pack — only then, never on its own — and the paragraph gets translated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadParagraphLanguageTest {

    @get:Rule val main = MainDispatcherRule()

    /** "de: …" paragraphs are German; for the book's language the detector answers English. */
    private class ByPrefix : LanguageDetector {
        override suspend fun identify(text: String): String = "en"
        override suspend fun identifyConfident(text: String): String? =
            text.substringBefore(':').takeIf { ':' in text && it.length == 2 }
    }

    private val tail = " — long enough to be told apart from a short reply or a name, by some margin."
    private val german = "de: Ein Brief$tail"
    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "C", paragraphs = listOf("An English paragraph$tail", german))),
    )

    private suspend fun TestScope.open(repo: FakeTranslationRepository): ReaderViewModelHarness {
        val h = ReaderViewModelHarness(
            book, prefs = FakeReadingPreferences(), repo = repo,
            settings = FakeTranslatorSettings(current = TranslationProvider.BERGAMOT),
            detector = ByPrefix(), ioDispatcher = main.dispatcher,
        )
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        awaitLoaded(h)
        h.vm.onVisibleRange(0, 0, 0, 1)
        return h
    }

    private fun ReaderViewModelHarness.success() = vm.uiState.value as ReaderUiState.Success

    @Test
    fun `the button downloads the model and the paragraph is translated`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository().apply { notInstalled += Language.GERMAN }
        val h = open(repo)
        assertTrue(pollUntil(5_000) { h.success().paragraphNotes[0]?.get(1) != null })
        assertEquals(ParagraphNote.NeedsModel(Language.GERMAN, TranslationProvider.BERGAMOT), h.success().paragraphNotes[0]?.get(1))
        assertEquals("nothing is downloaded before the reader asks", listOf(Language.ENGLISH), repo.prepared.distinct())

        h.vm.downloadModel(Language.GERMAN, TranslationProvider.BERGAMOT)

        assertTrue(pollUntil(5_000) { h.success().chapterTranslations[0]?.get(1) == "ru:$german" })
        assertEquals(ParagraphNote.TranslatedFrom(Language.GERMAN), h.success().paragraphNotes[0]?.get(1))
        assertEquals(emptyMap<Language, LanguageDownload>(), h.success().languageDownloads)
    }

    @Test
    fun `a word in the German paragraph is looked up as German, and not at all before the model is here`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository().apply { notInstalled += Language.GERMAN }
        val h = open(repo)
        assertTrue(pollUntil(5_000) { h.success().paragraphNotes[0]?.get(1) != null })
        repo.sources.clear()

        h.vm.selectWord("Brief", 0, 1, 8, 13)
        pollUntil(500) { false }
        assertEquals("no lookup (it would download the pack behind the reader's back)", emptyList<Language>(), repo.sources)
        // ... and the popup says why, pointing at the paragraph's button — not at the book's language
        // menu, which would change the whole book's language.
        val failed = h.success().wordSelection?.translation as? SelectionTranslation.Failed
        assertTrue("popup: $failed", failed?.message?.contains("Download and translate") == true)

        h.vm.downloadModel(Language.GERMAN, TranslationProvider.BERGAMOT)
        assertTrue(pollUntil(5_000) { h.success().paragraphNotes[0]?.get(1) is ParagraphNote.TranslatedFrom })
        repo.sources.clear()
        h.vm.selectWord("Brief", 0, 1, 8, 13)
        assertTrue(pollUntil(5_000) { repo.sources.isNotEmpty() })
        assertEquals(listOf(Language.GERMAN), repo.sources.distinct())

        assertTrue(pollUntil(5_000) { h.success().wordSelection?.translation is SelectionTranslation.Ready })
        h.vm.saveWord("Brief", 0, 1)
        assertTrue(pollUntil(5_000) { h.savedWords.words.value.isNotEmpty() })
        assertEquals("saved under the paragraph's language", "de", h.savedWords.words.value.single().sourceLang)
    }

    @Test
    fun `a new target language forgets downloads made for the old one`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository().apply {
            notInstalled += Language.GERMAN
            failPrepare += Language.GERMAN
        }
        val h = open(repo)
        assertTrue(pollUntil(5_000) { h.success().paragraphNotes[0]?.get(1) != null })
        h.vm.downloadModel(Language.GERMAN, TranslationProvider.BERGAMOT)
        assertTrue(pollUntil(5_000) { h.success().languageDownloads[Language.GERMAN] == LanguageDownload.Failed })

        h.vm.setTargetLanguage(Language.FRENCH)

        assertTrue(pollUntil(2_000) { h.success().languageDownloads.isEmpty() })
    }

    @Test
    fun `the book's language chosen by the reader is not overridden paragraph by paragraph`() = runTest(main.dispatcher) {
        // Detected (wrongly) as Spanish, every paragraph too; the reader says it is Portuguese.
        val spanishEverywhere = object : LanguageDetector {
            override suspend fun identify(text: String): String = "es"
            override suspend fun identifyConfident(text: String): String = "es"
        }
        val repo = FakeTranslationRepository()
        val h = ReaderViewModelHarness(
            book, prefs = FakeReadingPreferences(), repo = repo,
            settings = FakeTranslatorSettings(current = TranslationProvider.BERGAMOT),
            detector = spanishEverywhere, ioDispatcher = main.dispatcher,
        )
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        awaitLoaded(h)
        h.vm.setSourceLanguage(Language.PORTUGUESE)
        repo.sources.clear()
        h.vm.onVisibleRange(0, 0, 0, 1)

        assertTrue(pollUntil(5_000) { h.success().chapterTranslations[0]?.all { it.isNotEmpty() } == true })
        assertEquals(listOf(Language.PORTUGUESE), repo.sources.distinct())
        assertEquals(emptyMap<Int, ParagraphNote>(), h.success().paragraphNotes[0].orEmpty())
    }

    @Test
    fun `a failed download says so and leaves the paragraph as it is`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository().apply {
            notInstalled += Language.GERMAN
            failPrepare += Language.GERMAN
        }
        val h = open(repo)
        assertTrue(pollUntil(5_000) { h.success().paragraphNotes[0]?.get(1) != null })

        h.vm.downloadModel(Language.GERMAN, TranslationProvider.BERGAMOT)

        assertTrue(pollUntil(5_000) { h.success().languageDownloads[Language.GERMAN] == LanguageDownload.Failed })
        assertEquals(german, h.success().chapterTranslations[0]?.get(1))
    }
}
