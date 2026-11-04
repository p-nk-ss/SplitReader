package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ParagraphNote
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.usecase.ParagraphLanguage
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reader's side of paragraphs in another language (2026-11-04): the engine hands the UI a note
 * for each such paragraph, and a model the reader downloads from that note gets its paragraphs
 * translated — and nothing else retranslated.
 */
class ParagraphNotesEngineTest {

    /** "de: …" paragraphs are German, "la: …" Latin; the rest is unsure (the book's). */
    private class ByPrefix : LanguageDetector {
        override suspend fun identify(text: String): String? = null
        override suspend fun identifyConfident(text: String): String? =
            text.substringBefore(':').takeIf { ':' in text && it.length == 2 }
    }

    private class Repo : TranslationRepository {
        val installed = mutableSetOf(Language.ENGLISH)
        val calls = mutableListOf<String>()
        override fun resolve(provider: TranslationProvider, source: Language, target: Language) = Resolution.Ready(provider)
        override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
            calls += text
            return "ru:$text"
        }
        override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> = flow {
            emit(0.5f)
            installed += source
            emit(1f)
        }
        /** When set, isReady answers what it saw on entry only after this completes (a stale answer). */
        var isReadyGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun isReady(source: Language, target: Language, provider: TranslationProvider): Boolean {
            val ready = source in installed
            isReadyGate?.await()
            return ready
        }
        override suspend fun cachedCount() = 0
        override suspend fun clearCache() = Unit
    }

    private val tail = " — a paragraph long enough to be told apart from a short reply or a name."
    private val paragraphs = listOf("en$tail", "de: first$tail", "la: third$tail", "de: fourth$tail")
    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "C", paragraphs = paragraphs)),
    )

    private fun TestScope.engine(repo: Repo) = ReaderTranslationEngine(
        backgroundScope,
        TranslateTextUseCase(repo, StandardTestDispatcher(testScheduler), ParagraphLanguage(ByPrefix())),
    )

    private fun TestScope.opened(repo: Repo): ReaderTranslationEngine {
        val e = engine(repo)
        e.open(book, Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT, paneVisible = true)
        e.viewport(ReadingPosition(0, 0), ReadingPosition(0, 3))
        runCurrent()
        return e
    }

    @Test
    fun `the UI gets a note for every paragraph in another language`() = runTest {
        val e = opened(Repo())
        assertEquals(
            mapOf(
                1 to ParagraphNote.NeedsModel(Language.GERMAN, TranslationProvider.BERGAMOT),
                2 to ParagraphNote.Original("la"),
                3 to ParagraphNote.NeedsModel(Language.GERMAN, TranslationProvider.BERGAMOT),
            ),
            e.state.value.notes[0],
        )
        assertEquals("a paragraph waiting for its model shows the original", paragraphs[1], e.state.value.translations[0]?.get(1))
    }

    @Test
    fun `a downloaded model translates its paragraphs and nothing else again`() = runTest {
        val repo = Repo()
        val e = opened(repo)
        repo.calls.clear()

        e.languageReady(Language.GERMAN)
        repo.installed += Language.GERMAN
        runCurrent()

        assertEquals(listOf(paragraphs[1], paragraphs[3]), repo.calls)
        assertEquals("ru:${paragraphs[1]}", e.state.value.translations[0]?.get(1))
        assertEquals(ParagraphNote.TranslatedFrom(Language.GERMAN), e.state.value.notes[0]?.get(1))
        assertEquals(ParagraphNote.Original("la"), e.state.value.notes[0]?.get(2))
    }

    @Test
    fun `a stale 'needs model' answer that lands after the download does not bring the button back`() = runTest {
        val repo = Repo()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>().also { repo.isReadyGate = it }
        val e = engine(repo)
        e.open(book, Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT, paneVisible = true)
        e.viewport(ReadingPosition(0, 0), ReadingPosition(0, 3))
        runCurrent() // paragraph 1 has checked for the German pack (absent) and is held

        repo.installed += Language.GERMAN
        e.languageReady(Language.GERMAN)
        runCurrent()
        repo.isReadyGate = null
        gate.complete(Unit)
        runCurrent()

        assertEquals("ru:${paragraphs[1]}", e.state.value.translations[0]?.get(1))
        assertEquals(ParagraphNote.TranslatedFrom(Language.GERMAN), e.state.value.notes[0]?.get(1))
    }
}
