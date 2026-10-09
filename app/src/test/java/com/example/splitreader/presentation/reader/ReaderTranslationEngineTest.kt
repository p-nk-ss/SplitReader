package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Translates to "<target code>:<text>"; can fail or block chosen paragraphs; records every call. */
private class FakeRepo : TranslationRepository {
    val calls: MutableList<Pair<String, Language>> = Collections.synchronizedList(mutableListOf())
    val failOn: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        calls += text to targetLanguage
        // An engine that re-plans the same work forever would spin runCurrent() on the test thread,
        // where runTest's timeout cannot reach it. An Error (not an Exception, which the use case
        // swallows per paragraph) kills the segment job, so the test fails instead of hanging.
        if (calls.size > RUNAWAY_LIMIT) throw AssertionError("runaway engine: >$RUNAWAY_LIMIT calls, last=$text")
        gates[text]?.await()
        if (text in failOn) throw IOException("down: $text")
        return "${targetLanguage.code}:$text"
    }
    override fun prepare(sourceLanguage: Language, targetLanguage: Language): Flow<Float> = emptyFlow()
    override suspend fun cachedCount() = 0
    override suspend fun clearCache() = Unit

    fun texts() = calls.map { it.first }

    private companion object {
        /** Far above any test's legitimate call count (the largest book here has 60 paragraphs). */
        const val RUNAWAY_LIMIT = 1_000
    }
}

/** Chapter c has `sizes[c]` paragraphs named "c<c>p<i>". */
private fun book(vararg sizes: Int) = Book(
    title = "T", author = "A", filePath = "/b",
    chapters = sizes.mapIndexed { c, n -> Chapter(index = c, title = "C$c", paragraphs = List(n) { "c${c}p$it" }) },
)

private fun p(chapter: Int, paragraph: Int) = ReadingPosition(chapter, paragraph)

private val PAID = TranslationProvider.QUICK_TRANSLATE
private val FREE = TranslationProvider.MLKIT

class ReaderTranslationEngineTest {

    private fun TestScope.engine(repo: FakeRepo) =
        ReaderTranslationEngine(backgroundScope, TranslateTextUseCase(repo, StandardTestDispatcher(testScheduler)))

    /**
     * Drain all pending work. The engine (actor and segment jobs) lives on `backgroundScope`, and
     * `advanceUntilIdle()` stops as soon as only background tasks remain — it would never run the
     * engine. `runCurrent()` runs every task at the current virtual time, background ones included;
     * the engine never advances virtual time, so this drains it completely.
     */
    private fun TestScope.settle() = runCurrent()

    private fun ReaderTranslationEngine.text(chapter: Int, paragraph: Int) =
        state.value.translations[chapter]?.getOrNull(paragraph)

    @Test
    fun `E1 a target change retranslates the whole remembered viewport, not one anchor paragraph`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(20), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 9))
        settle()

        e.settingsChanged(Language.GERMAN, PAID, paneVisible = true)
        settle()

        for (i in 0..9) assertEquals("paragraph $i", "de:c0p$i", e.text(0, i))
    }

    /**
     * Cancellation alone prevents the stale result in this single-threaded test; the epoch check is
     * defence-in-depth for results already queued before the cancel and is not demonstrated here.
     */
    @Test
    fun `E2 no old-language text survives a target change made while a segment is in flight`() = runTest {
        val repo = FakeRepo()
        val gate = CompletableDeferred<Unit>().also { repo.gates["c0p0"] = it }
        val e = engine(repo)
        e.open(book(5), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 0))
        runCurrent() // c0p0 (ru) is now blocked inside the repository

        e.settingsChanged(Language.GERMAN, PAID, paneVisible = true)
        repo.gates.remove("c0p0")
        gate.complete(Unit)
        settle()

        val all = e.state.value.translations.values.flatten().filter { it.isNotEmpty() }
        assertTrue("old-language text leaked: $all", all.none { it.startsWith("ru:") })
        assertEquals("de:c0p0", e.text(0, 0))
    }

    @Test
    fun `E3 fillChapter sends every paragraph to the repository exactly once`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(30), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 4))
        settle()
        e.fillChapter(0)
        settle()

        val counts = repo.texts().groupingBy { it }.eachCount()
        for (i in 0 until 30) assertEquals("c0p$i", 1, counts["c0p$i"])
    }

    @Test
    fun `E4 a fillChapter failure is shown in the banner`() = runTest {
        val repo = FakeRepo().apply { failOn += "c0p20" }
        val e = engine(repo)
        e.open(book(30), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 4))
        settle()
        e.fillChapter(0)
        settle()

        assertTrue("banner: ${e.state.value.state}", e.state.value.state is TranslationState.Error)
    }

    @Test
    fun `E5 a new viewport during a chapter fill is translated before the rest of the chapter`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(40), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 4))
        settle() // 0..12 done (visible + lookahead)

        val gate = CompletableDeferred<Unit>().also { repo.gates["c0p13"] = it }
        e.fillChapter(0)
        runCurrent() // first fill chunk (13..20) blocked at c0p13
        e.viewport(p(0, 30), p(0, 34))
        repo.gates.remove("c0p13")
        gate.complete(Unit)
        settle()

        val order = repo.texts()
        assertTrue(
            "visible c0p30 must come before fill c0p21: $order",
            order.indexOf("c0p30") < order.indexOf("c0p21"),
        )
    }

    @Test
    fun `E6 retry after a visible-window failure translates everything and clears the error`() = runTest {
        val repo = FakeRepo().apply { failOn += "c0p3" }
        val e = engine(repo)
        e.open(book(10), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 5))
        settle()
        assertTrue(e.state.value.state is TranslationState.Error)

        repo.failOn.clear()
        e.retry()
        settle()

        assertEquals(TranslationState.Idle, e.state.value.state)
        for (i in 0..5) assertEquals("ru:c0p$i", e.text(0, i))
    }

    @Test
    fun `E7 a background failure stays silent, pauses background work, and the next viewport resumes it`() = runTest {
        val repo = FakeRepo().apply { failOn += "c0p16" } // inside the lookahead of window 10..14
        val e = engine(repo)
        e.open(book(30), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 10), p(0, 14))
        settle()

        assertFalse("banner must stay quiet: ${e.state.value.state}", e.state.value.state is TranslationState.Error)
        assertFalse("lookbehind must wait after a background failure", "c0p6" in repo.texts())
        assertEquals(
            "the failed background paragraph must not be retried before the next event",
            1, repo.texts().count { it == "c0p16" },
        )

        repo.failOn.clear()
        e.viewport(p(0, 10), p(0, 14))
        settle()
        assertEquals("ru:c0p6", e.text(0, 6))
        assertEquals("ru:c0p16", e.text(0, 16))
    }

    @Test
    fun `E8 a paid engine is silent while the pane is hidden, keeps its work, and a free engine is not gated`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(20), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = false)
        e.viewport(p(0, 0), p(0, 4))
        settle()
        assertTrue("no paid calls while hidden: ${repo.calls}", repo.calls.isEmpty())

        e.settingsChanged(Language.RUSSIAN, PAID, paneVisible = true)
        settle()
        assertEquals("ru:c0p0", e.text(0, 0))

        val before = repo.calls.size
        e.settingsChanged(Language.RUSSIAN, PAID, paneVisible = false)
        e.viewport(p(0, 15), p(0, 19))
        settle()
        assertEquals("nothing translated while hidden", before, repo.calls.size)
        assertEquals("hiding keeps finished work", "ru:c0p0", e.text(0, 0))

        e.settingsChanged(Language.RUSSIAN, PAID, paneVisible = true)
        settle()
        assertEquals("ru:c0p15", e.text(0, 15))
        val counts = repo.texts().groupingBy { it }.eachCount()
        assertTrue("nothing translated twice: $counts", counts.values.all { it == 1 })

        val freeRepo = FakeRepo()
        val free = engine(freeRepo)
        free.open(book(5), Language.ENGLISH, Language.RUSSIAN, FREE, paneVisible = false)
        free.viewport(p(0, 0), p(0, 1))
        settle()
        assertEquals("ru:c0p0", free.text(0, 0))
    }

    @Test
    fun `E9 concurrent calls from other threads leave a coherent state`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(60), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        withContext(Dispatchers.Default) {
            repeat(50) { i -> launch { e.viewport(p(0, i), p(0, i)) } }
        }
        settle()

        val counts = repo.texts().groupingBy { it }.eachCount()
        assertTrue("no paragraph translated twice: $counts", counts.values.all { it == 1 })
        for ((text, _) in counts) {
            val i = text.removePrefix("c0p").toInt()
            assertEquals("ru:$text", e.text(0, i))
        }
    }

    @Test
    fun `E10 calls before open are ignored and a new open starts clean`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.viewport(p(0, 0), p(0, 3))
        e.fillChapter(0)
        e.retry()
        settle()
        assertTrue(repo.calls.isEmpty())

        e.open(book(5), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 1))
        settle()
        assertEquals("ru:c0p0", e.text(0, 0))

        e.open(book(3), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        settle()
        // The new open resets the viewport to START, so the new book's 3 paragraphs (window + lookahead)
        // are translated — and nothing of the old 5-paragraph chapter may remain.
        assertEquals(
            mapOf(0 to listOf("ru:c0p0", "ru:c0p1", "ru:c0p2")),
            e.state.value.translations,
        )
    }
}
