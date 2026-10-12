package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
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
    /** What [resolve] answers for each provider; Ready by default. */
    var resolution: (TranslationProvider) -> Resolution = { Resolution.Ready(it) }
    val calls: MutableList<Pair<String, Language>> = Collections.synchronizedList(mutableListOf())
    val failOn: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val cancelOn: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override fun resolve(provider: TranslationProvider, source: Language, target: Language) = resolution(provider)

    override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
        calls += text to target
        // An engine that re-plans the same work forever would spin runCurrent() on the test thread,
        // where runTest's timeout cannot reach it. Past the limit, the first call reports the runaway
        // (an Error, which the use case does not swallow) and every later call parks forever: an
        // engine that survives the Error and re-plans still stops, so the test fails instead of hanging.
        if (calls.size > RUNAWAY_LIMIT) {
            if (calls.size == RUNAWAY_LIMIT + 1) throw AssertionError("runaway engine: >$RUNAWAY_LIMIT calls, last=$text")
            awaitCancellation()
        }
        gates[text]?.await()
        if (text in failOn) throw IOException("down: $text")
        // What a provider's own withTimeout, or a cancelled Play-services Task.await(), throws while
        // the caller is still active; TranslateTextUseCase rethrows every CancellationException.
        if (text in cancelOn) throw CancellationException("timed out: $text")
        return "${target.code}:$text"
    }
    override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> = emptyFlow()
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
     *
     * It does not advance virtual time either: a delay-based fake, or the use case's 429 backoff
     * path, would stall here, and an absence assertion made after such a stall would pass vacuously.
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
            // Viewports start at 10 so none coincides with open's default START window (0..8).
            repeat(50) { i -> launch { e.viewport(p(0, i + 10), p(0, i + 10)) } }
        }
        settle()

        val counts = repo.texts().groupingBy { it }.eachCount()
        assertTrue("no paragraph translated twice: $counts", counts.values.all { it == 1 })
        for ((text, _) in counts) {
            val i = text.removePrefix("c0p").toInt()
            assertEquals("ru:$text", e.text(0, i))
        }
        // Whichever viewport the engine saw last, its whole window (look-behind 4, look-ahead 8) is done.
        val windowDone = (10..59).any { v -> ((v - 4)..minOf(v + 8, 59)).all { e.text(0, it) == "ru:c0p$it" } }
        assertTrue("no viewport's window was translated: $counts", windowDone)
    }

    /**
     * The "before open" half cannot be made to fail: `step()` returns without a book, so even an
     * engine that applied those events would issue no work. Only the "new open starts clean" half
     * is demonstrated (by skipping `discardTranslations()` in `Open`).
     */
    @Test
    fun `E10 calls before open are harmless and a new open starts clean`() = runTest {
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

    @Test
    fun `E11 hiding the pane with a paid provider cancels the running segment`() = runTest {
        val repo = FakeRepo()
        val gate = CompletableDeferred<Unit>().also { repo.gates["c0p1"] = it }
        val e = engine(repo)
        e.open(book(20), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 4))
        settle() // visible segment 0..4 is blocked at c0p1

        e.settingsChanged(Language.RUSSIAN, PAID, paneVisible = false)
        repo.gates.remove("c0p1")
        gate.complete(Unit)
        settle()

        val texts = repo.texts()
        for (i in 2..4) assertFalse("c0p$i requested after the pane was hidden: $texts", "c0p$i" in texts)
        assertEquals("finished work is kept", "ru:c0p0", e.text(0, 0))
    }

    /**
     * A CancellationException from below while the segment's own job is still active — what a
     * provider's internal `withTimeout` or a cancelled Play-services `Task.await()` throws, and what
     * TranslateTextUseCase deliberately rethrows — must end the segment as a failure, not wedge the engine.
     */
    @Test
    fun `E12 a stray cancellation from the provider is an error, not a wedged engine`() = runTest {
        val repo = FakeRepo().apply { cancelOn += "c0p2" }
        val e = engine(repo)
        e.open(book(10), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 4))
        settle()
        assertTrue("banner: ${e.state.value.state}", e.state.value.state is TranslationState.Error)

        repo.cancelOn.clear()
        e.retry()
        settle()
        for (i in 0..4) assertEquals("ru:c0p$i", e.text(0, i))
        assertEquals(TranslationState.Idle, e.state.value.state)
    }

    @Test
    fun `E13 invalidate discards translations and retranslates the remembered viewport`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(20), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true)
        e.viewport(p(0, 0), p(0, 4))
        settle()
        for (i in 0..4) assertEquals("precondition", "ru:c0p$i", e.text(0, i))

        e.invalidate()
        settle()

        val counts = repo.texts().groupingBy { it }.eachCount()
        for (i in 0..4) assertEquals("c0p$i requested again after invalidate: $counts", 2, counts["c0p$i"])
        for (i in 0..4) assertEquals("ru:c0p$i", e.text(0, i))
    }

    @Test
    fun `E14 open seeds the restored position — the first request is the restored paragraph, not the book start`() = runTest {
        val repo = FakeRepo()
        val e = engine(repo)
        e.open(book(40), Language.ENGLISH, Language.RUSSIAN, PAID, paneVisible = true, start = p(0, 30))
        settle()

        val texts = repo.texts()
        assertEquals("first request: $texts", "c0p30", texts.first())
        assertFalse("the book start was requested: $texts", "c0p0" in texts)
    }
}
