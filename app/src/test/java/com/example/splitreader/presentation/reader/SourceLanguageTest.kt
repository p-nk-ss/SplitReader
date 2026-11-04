package com.example.splitreader.presentation.reader

import android.app.Application
import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeTranslationRepository
import com.example.splitreader.fakes.FakeTranslatorSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Detecting the book's language. Found on a tablet emulator with 25 books (2026-11-04): the old
 * detector read only the first 500 characters from chapter 2 on, so Don Quixote (Latin epigraphs
 * first) and Rashomon (an English digitizer's note first) came out English, and any language outside
 * the 18 silently became English and was "translated" as English.
 *
 * Paragraph text carries a marker ("lat_", "spa_", …); [MarkerDetector] answers with the language most
 * of the text is marked with, as ML Kit answers for a mixed sample.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SourceLanguageTest {

    @get:Rule val main = MainDispatcherRule()

    private fun para(marker: String, n: Int) = "$marker paragraph number $n " + "words ".repeat(18)

    /** A title page, then chapters built from (marker, count) runs. */
    private fun book(vararg chapters: List<Pair<String, Int>>) = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "Title", paragraphs = listOf("T", "A"))) +
            chapters.mapIndexed { i, runs ->
                Chapter(index = i + 1, title = "C${i + 1}", paragraphs = runs.flatMap { (m, k) -> List(k) { para(m, it) } })
            },
    )

    private fun harness(book: Book, repo: FakeTranslationRepository = FakeTranslationRepository()) = ReaderViewModelHarness(
        book,
        prefs = FakeReadingPreferences(),
        repo = repo,
        settings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE),
        detector = MarkerDetector(),
        ioDispatcher = main.dispatcher,
    )

    private suspend fun TestScope.open(h: ReaderViewModelHarness): ReaderUiState.Success {
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        return awaitLoaded(h)
    }

    @Test
    fun `a Spanish book that opens with Latin epigraphs is Spanish`() = runTest(main.dispatcher) {
        val spanish = List(6) { listOf("spa_" to 30) }
        val h = harness(book(listOf("lat_" to 6, "spa_" to 30), *spanish.toTypedArray()))
        assertEquals(Language.SPANISH, open(h).sourceLanguage)
    }

    @Test
    fun `a Japanese book that opens with an English note is Japanese`() = runTest(main.dispatcher) {
        val japanese = List(6) { listOf("jpn_" to 30) }
        val h = harness(book(listOf("eng_" to 6, "jpn_" to 30), *japanese.toTypedArray()))
        assertEquals(Language.JAPANESE, open(h).sourceLanguage)
    }

    @Test
    fun `a book in a language the translators lack is not translated as English`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository()
        val h = harness(book(*List(6) { listOf("fin_" to 30) }.toTypedArray()), repo)
        open(h)
        h.vm.onVisibleRange(1, 0, 1, 9)
        advanceUntilIdle()
        assertTrue("Finnish text was sent to the translator as English: ${repo.texts().take(2)}", repo.texts().isEmpty())
    }
}

/**
 * The reader's own choice of the book's language. Detection can be wrong, and for a language the
 * translators lack it is the only way to read the book translated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChosenSourceLanguageTest {

    @get:Rule val main = MainDispatcherRule()

    private fun finnishBook() = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = List(4) { c -> Chapter(index = c, title = "C$c", paragraphs = List(20) { "fin_ paragraph $c-$it " + "words ".repeat(18) }) },
    )

    private fun harness(prefs: FakeReadingPreferences, repo: FakeTranslationRepository) = ReaderViewModelHarness(
        finnishBook(), prefs = prefs, repo = repo,
        settings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE),
        detector = MarkerDetector(), ioDispatcher = main.dispatcher,
    )

    private suspend fun TestScope.open(h: ReaderViewModelHarness): ReaderUiState.Success {
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        return awaitLoaded(h)
    }

    @Test
    fun `a chosen language translates the book and is remembered for it`() = runTest(main.dispatcher) {
        val prefs = FakeReadingPreferences()
        val repo = FakeTranslationRepository()
        val h = harness(prefs, repo)
        assertEquals(false, open(h).translatable)

        h.vm.setSourceLanguage(Language.GERMAN)
        h.vm.onVisibleRange(0, 0, 0, 5)
        advanceUntilIdle()
        assertTrue("nothing translated after choosing German", repo.texts().isNotEmpty())
        assertTrue("translated from ${repo.sources.toSet()}", repo.sources.all { it == Language.GERMAN })

        // Opened again: the choice holds, without the reader picking it twice.
        val again = harness(prefs, FakeTranslationRepository())
        val state = open(again)
        assertEquals(Language.GERMAN, state.chosenSource)
        assertEquals(Language.GERMAN, state.sourceLanguage)
        assertEquals(true, state.translatable)
    }

    @Test
    fun `going back to automatic stops translating a book in an unsupported language`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository()
        val h = harness(FakeReadingPreferences(), repo)
        open(h)
        h.vm.setSourceLanguage(Language.GERMAN)
        h.vm.onVisibleRange(0, 0, 0, 5)
        advanceUntilIdle()

        h.vm.setSourceLanguage(null)
        advanceUntilIdle()
        val before = repo.texts().size
        h.vm.onVisibleRange(1, 0, 1, 5)
        advanceUntilIdle()
        val state = h.vm.uiState.value as ReaderUiState.Success
        assertEquals(false, state.translatable)
        assertTrue("old translations still shown: ${state.chapterTranslations.keys}", state.chapterTranslations.isEmpty())
        assertEquals("translated again after going back to automatic", before, repo.texts().size)
    }

    @Test
    fun `choosing the book's language as the target's switches the target`() = runTest(main.dispatcher) {
        val prefs = FakeReadingPreferences().apply { targetLanguage = Language.GERMAN }
        val h = harness(prefs, FakeTranslationRepository())
        open(h)
        h.vm.setSourceLanguage(Language.GERMAN)
        advanceUntilIdle()
        assertEquals(Language.ENGLISH, (h.vm.uiState.value as ReaderUiState.Success).targetLanguage)
    }
}

/** Answers with the code of the language most of [text] is marked with, as ML Kit does for a mixed stretch. */
internal class MarkerDetector : LanguageDetector {
    override suspend fun identify(text: String): String? =
        MARKERS.entries.maxByOrNull { (m, _) -> Regex(Regex.escape(m)).findAll(text).count() }
            ?.takeIf { (m, _) -> m in text }?.value

    companion object {
        val MARKERS = mapOf("spa_" to "es", "jpn_" to "ja", "eng_" to "en", "ger_" to "de", "lat_" to "la", "fin_" to "fi")
    }
}
