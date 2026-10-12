package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.FallbackReason
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Translates to "<target code>:<text>"; can block chosen texts; records every call and every finish. */
private class SelectionFakeRepo : TranslationRepository {
    /** What [resolve] answers for each provider; Ready by default. */
    var resolution: (TranslationProvider) -> Resolution = { Resolution.Ready(it) }
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Texts whose request ran past its gate, i.e. was not cancelled while blocked. */
    val finished: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Texts whose [translate] throws a foreign [CancellationException] (e.g. a closed ML Kit translator). */
    val interrupted = mutableSetOf<String>()

    /** Progress values [prepare] emits (on-device providers only). */
    var prepareProgress: List<Float> = emptyList()

    override fun resolve(provider: TranslationProvider, source: Language, target: Language) = resolution(provider)

    override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
        calls += text
        gates[text]?.await()
        if (text in interrupted) throw CancellationException("translator closed")
        finished += text
        return "${target.code}:$text"
    }

    override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> =
        prepareProgress.asFlow()

    override suspend fun cachedCount() = 0
    override suspend fun clearCache() = Unit
}

private val SEL_PAID = TranslationProvider.QUICK_TRANSLATE

class SelectionTranslatorTest {

    private fun TestScope.translator(repo: SelectionFakeRepo) =
        SelectionTranslator(backgroundScope, TranslateTextUseCase(repo, StandardTestDispatcher(testScheduler)))

    /**
     * Drains pending work. The module runs on `backgroundScope`, which `advanceUntilIdle()` skips;
     * `runCurrent()` runs every task at the current virtual time, background ones included.
     */
    private fun TestScope.settle() = runCurrent()

    /** Every state the module publishes, in order (an unconfined collector sees each value). */
    private fun TestScope.history(t: SelectionTranslator): List<WordSelection?> {
        val seen = Collections.synchronizedList(mutableListOf<WordSelection?>())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { t.state.collect { seen += it } }
        return seen
    }

    private fun SelectionTranslator.pick(
        word: String,
        chapter: Int = 0,
        paragraph: Int = 1,
        start: Int = 0,
        end: Int = word.length,
        provider: TranslationProvider = SEL_PAID,
    ) = select(word, chapter, paragraph, start, end, Language.ENGLISH, Language.RUSSIAN, provider, SelectionType.WORD)

    @Test
    fun `select translates once and becomes Ready`() = runTest {
        val repo = SelectionFakeRepo()
        val t = translator(repo)

        t.pick("run")
        settle()

        assertEquals(listOf("run"), repo.calls)
        assertEquals(SelectionTranslation.Ready("ru:run"), t.state.value?.translation)
    }

    @Test
    fun `dragging requests nothing until release, then exactly once`() = runTest {
        val repo = SelectionFakeRepo()
        val t = translator(repo)
        t.pick("run", start = 0, end = 3)
        settle()
        repo.calls.clear()

        t.dragTo("run f", 0, 5, SelectionType.SENTENCE)
        settle()
        t.dragTo("run fa", 0, 6, SelectionType.SENTENCE)
        settle()
        t.dragTo("run fast", 0, 8, SelectionType.SENTENCE)
        settle()

        assertEquals("no request while dragging", emptyList<String>(), repo.calls)
        assertEquals(SelectionTranslation.Pending, t.state.value?.translation)

        t.dragEnd()
        settle()

        assertEquals(listOf("run fast"), repo.calls)
        assertEquals(SelectionTranslation.Ready("ru:run fast"), t.state.value?.translation)
    }

    @Test
    fun `releasing without a change after Ready requests nothing`() = runTest {
        val repo = SelectionFakeRepo()
        val t = translator(repo)
        t.pick("run")
        settle()
        repo.calls.clear()

        t.dragEnd()
        settle()

        assertEquals(emptyList<String>(), repo.calls)
        assertEquals(SelectionTranslation.Ready("ru:run"), t.state.value?.translation)
    }

    /**
     * Three guards drop the stale result: cancelling the previous request, the request number, and
     * the chapter/paragraph/offset match. Any one of them is enough in this single-threaded test,
     * so removing a single guard stays green; the test fails only with all three removed. The
     * number and the match are defence-in-depth for a result queued before the cancel.
     */
    @Test
    fun `a late result from a previous selection is dropped`() = runTest {
        val repo = SelectionFakeRepo()
        val gate = CompletableDeferred<Unit>().also { repo.gates["a"] = it }
        val t = translator(repo)
        val seen = history(t)

        t.pick("a", chapter = 0, paragraph = 1, start = 0, end = 1)
        settle()
        t.pick("b", chapter = 0, paragraph = 2, start = 0, end = 1)
        gate.complete(Unit)
        settle()

        assertEquals("b", t.state.value?.word)
        assertEquals(SelectionTranslation.Ready("ru:b"), t.state.value?.translation)
        assertTrue(
            "the stale translation was published: $seen",
            seen.none { (it?.translation as? SelectionTranslation.Ready)?.text == "ru:a" },
        )
    }

    @Test
    fun `model download shows progress`() = runTest {
        val repo = SelectionFakeRepo().apply { prepareProgress = listOf(0.5f) }
        val gate = CompletableDeferred<Unit>().also { repo.gates["run"] = it }
        val t = translator(repo)

        t.pick("run", provider = TranslationProvider.MLKIT)
        settle()

        assertEquals(SelectionTranslation.Translating(0.5f), t.state.value?.translation)

        gate.complete(Unit)
        settle()

        assertEquals(SelectionTranslation.Ready("ru:run"), t.state.value?.translation)
    }

    @Test
    fun `a finished download shows translating, not 100 percent`() = runTest {
        val repo = SelectionFakeRepo().apply { prepareProgress = listOf(0.5f, 1f) }
        val gate = CompletableDeferred<Unit>().also { repo.gates["run"] = it }
        val t = translator(repo)

        t.pick("run", provider = TranslationProvider.MLKIT)
        settle()

        assertEquals(SelectionTranslation.Translating(null), t.state.value?.translation)

        gate.complete(Unit)
        settle()

        assertEquals(SelectionTranslation.Ready("ru:run"), t.state.value?.translation)
    }

    @Test
    fun `a fallback note travels with the translation`() = runTest {
        val repo = SelectionFakeRepo().apply {
            resolution = { Resolution.Fallback(it, TranslationProvider.MLKIT, FallbackReason.UNSUPPORTED_PAIR) }
        }
        val t = translator(repo)

        t.pick("run")
        settle()

        val ready = t.state.value?.translation as? SelectionTranslation.Ready
        assertNotNull("not Ready: ${t.state.value?.translation}", ready)
        assertEquals("ru:run", ready!!.text)
        assertNotNull("the fallback notice was lost", ready.notice)
    }

    @Test
    fun `an error becomes Failed with the short text`() = runTest {
        val repo = SelectionFakeRepo().apply { resolution = { Resolution.NotConfigured(it) } }
        val t = translator(repo)

        t.pick("run", provider = TranslationProvider.DEEPL)
        settle()

        assertEquals(
            SelectionTranslation.Failed("${TranslationProvider.DEEPL.displayName} API key isn't set"),
            t.state.value?.translation,
        )
    }

    /**
     * A dismissed request must stop, not keep running (and billing a paid provider) in the
     * background: [SelectionFakeRepo.finished] proves the blocked call never got past its gate.
     * The "no Ready" check is additionally guarded by `apply` ignoring results once the selection
     * is null; `clear`'s request bump is redundant with that and the cancel.
     */
    @Test
    fun `clear cancels and empties`() = runTest {
        val repo = SelectionFakeRepo()
        val gate = CompletableDeferred<Unit>().also { repo.gates["run"] = it }
        val t = translator(repo)
        val seen = history(t)

        t.pick("run")
        settle()
        t.clear()
        gate.complete(Unit)
        settle()

        assertNull(t.state.value)
        assertEquals("the dismissed request kept running", emptyList<String>(), repo.finished)
        assertTrue("a Ready was published: $seen", seen.none { it?.translation is SelectionTranslation.Ready })
    }

    /**
     * A tap's request is cancelled by the drag that follows it, not left running (and billing a
     * paid provider) until release: [SelectionFakeRepo.finished] proves it never got past its gate.
     */
    @Test
    fun `dragging cancels the tap's in-flight request`() = runTest {
        val repo = SelectionFakeRepo()
        val gate = CompletableDeferred<Unit>().also { repo.gates["run"] = it }
        val t = translator(repo)

        t.pick("run", start = 0, end = 3)
        settle()
        t.dragTo("run fast", 0, 8, SelectionType.SENTENCE)
        gate.complete(Unit)
        settle()

        assertTrue("the tap's request kept running after the drag: ${repo.finished}", "run" !in repo.finished)
    }

    /**
     * A CancellationException that isn't ours (ML Kit closing its shared translator because the
     * engine asked for another pair) must not end the request silently at Translating forever.
     */
    @Test
    fun `a foreign cancellation becomes Failed, not Translating forever`() = runTest {
        val repo = SelectionFakeRepo().apply { interrupted += "run" }
        val t = translator(repo)

        t.pick("run")
        settle()

        assertEquals(
            SelectionTranslation.Failed("Translation was interrupted — tap the word again"),
            t.state.value?.translation,
        )
    }
}
