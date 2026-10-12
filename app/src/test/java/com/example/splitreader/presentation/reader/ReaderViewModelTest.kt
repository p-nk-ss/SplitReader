package com.example.splitreader.presentation.reader

import android.app.Application
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.fakes.FakeLegacyReadingPositionStore
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeTranslationRepository
import com.example.splitreader.fakes.FakeTranslatorSettings
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Chapter c has `sizes[c]` paragraphs named "c<c>p<i>". */
private fun book(vararg sizes: Int) = Book(
    title = "T", author = "A", filePath = "/b",
    chapters = sizes.mapIndexed { c, n -> Chapter(index = c, title = "C$c", paragraphs = List(n) { "c${c}p$it" }) },
)

/**
 * A real ReaderViewModel over fakes: the wiring between the VM's user actions and the translation
 * engine, the reading position and the legacy migration. The engine runs on viewModelScope, which
 * the rule puts on the test dispatcher, so `advanceUntilIdle()` drives it to completion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReaderViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun harness(
        book: Book,
        prefs: FakeReadingPreferences = FakeReadingPreferences(),
        repo: FakeTranslationRepository = FakeTranslationRepository(),
        legacy: FakeLegacyReadingPositionStore = FakeLegacyReadingPositionStore(),
        settings: FakeTranslatorSettings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE),
    ) = ReaderViewModelHarness(
        book, prefs = prefs, repo = repo, legacy = legacy, settings = settings, ioDispatcher = main.dispatcher,
    )

    /** Subscribes to uiState (it is WhileSubscribed), opens the book and waits for it. */
    private suspend fun TestScope.open(h: ReaderViewModelHarness): ReaderUiState.Success {
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        return awaitLoaded(h)
    }

    private fun ReaderViewModelHarness.success() = vm.uiState.value as ReaderUiState.Success

    private fun ReaderViewModelHarness.text(chapter: Int, paragraph: Int) =
        success().chapterTranslations[chapter]?.getOrNull(paragraph)

    @Test
    fun `a language change retranslates every visible paragraph`() = runTest(main.dispatcher) {
        val h = harness(book(20))
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 9)
        advanceUntilIdle()

        h.vm.setTargetLanguage(Language.GERMAN)
        advanceUntilIdle()

        for (i in 0..9) assertTrue("c0p$i in German", ("c0p$i" to Language.GERMAN) in h.repo.calls)
    }

    @Test
    fun `hiding the pane stops a paid provider and showing it resumes`() = runTest(main.dispatcher) {
        val h = harness(book(20))
        open(h)
        advanceUntilIdle()

        h.vm.toggleTranslation() // hide
        h.vm.onVisibleRange(0, 10, 0, 14)
        advanceUntilIdle()
        val hidden = (10..14).map { "c0p$it" }.filter { it in h.repo.texts() }
        assertTrue("translated while hidden: $hidden", hidden.isEmpty())

        h.vm.toggleTranslation() // show
        advanceUntilIdle()
        for (i in 10..14) assertTrue("c0p$i after showing", "c0p$i" in h.repo.texts())
    }

    @Test
    fun `whole-chapter reaches the engine`() = runTest(main.dispatcher) {
        val h = harness(book(30))
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 2)
        advanceUntilIdle()

        h.vm.translateWholeChapter()
        advanceUntilIdle()

        val texts = h.repo.texts()
        for (i in 0 until 30) assertTrue("c0p$i", "c0p$i" in texts)
    }

    @Test
    fun `retry reaches the engine`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository().apply { failOn += "c0p1" }
        val h = harness(book(30), repo = repo)
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 2)
        advanceUntilIdle()
        assertTrue("expected Error, was ${h.success().translationState}", h.success().translationState is TranslationState.Error)

        repo.failOn.clear()
        h.vm.retryTranslation()
        advanceUntilIdle()

        assertEquals(TranslationState.Idle, h.success().translationState)
        assertEquals("ru:c0p1", h.text(0, 1))
    }

    /**
     * Saved at (1, 0, 0): what the reader persists at the first real paragraph of a book whose
     * first chapter is empty. A saved START would not discriminate: it coerces to START, which a
     * plain `== START` check also treats as "not started".
     */
    @Test
    fun `an empty first chapter does not mark the book started`() = runTest(main.dispatcher) {
        val h = harness(book(0, 3))
        h.prefs.positions[h.uri.toString()] = ReadingPosition(1, 0, 0)
        val loaded = open(h)
        assertNull("pendingJump after load", loaded.pendingJump)

        h.vm.onReadingPositionChanged(ReadingPosition(1, 0, 0))
        assertTrue("excerpt at the first real paragraph: ${h.prefs.excerpts}", h.prefs.excerpts.isEmpty())

        h.vm.onReadingPositionChanged(ReadingPosition(1, 1, 0))
        assertEquals("c1p1", h.prefs.excerpts[h.uri.toString()])
    }

    @Test
    fun `a migration failure still opens the book and is reported`() = runTest(main.dispatcher) {
        val h = harness(book(3), legacy = FakeLegacyReadingPositionStore(throwOnCheck = true))
        open(h)

        assertEquals(1, h.crash.recorded.size)
        assertEquals("Legacy reading-position migration failed", h.crash.recorded.single().second)
    }

    @Test
    fun `jumping to a bookmark sets and then clears pendingJump`() = runTest(main.dispatcher) {
        val h = harness(book(3, 4))
        open(h)

        h.vm.jumpToBookmark(1, 2)
        advanceUntilIdle()
        assertEquals(ReadingPosition(1, 2, 0), h.success().pendingJump)

        h.vm.consumePendingJump()
        advanceUntilIdle()
        assertNull(h.success().pendingJump)
    }

    /**
     * The engine resets asynchronously, so the VM still shows the old translations for the frames
     * between setTargetLanguage and the engine's reset (observed: exactly one such frame). From the
     * reset on (the first snapshot that differs from the pre-change translations) no snapshot may
     * carry Russian text. StateFlow conflation may skip snapshots, which can only hide a leak.
     *
     * What this pins: the VM forwards the change and the engine's reset clears the old translations
     * and re-translates the viewport. It cannot catch a late old-language result: the viewport is
     * fully translated before the change, so no old-epoch segment is in flight when it happens. That
     * epoch guard is covered by ReaderTranslationEngineTest (E2), not here.
     */
    @Test
    fun `a target change clears translations and no old-language text appears later`() = runTest(main.dispatcher) {
        val h = harness(book(20))
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 9)
        advanceUntilIdle()
        assertEquals("precondition", "ru:c0p0", h.text(0, 0))
        val before = h.success().chapterTranslations

        val seen: MutableList<Map<Int, List<String>>> = Collections.synchronizedList(mutableListOf())
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.uiState.collect { (it as? ReaderUiState.Success)?.let { s -> seen += s.chapterTranslations } }
        }
        h.vm.setTargetLanguage(Language.GERMAN)
        advanceUntilIdle()

        val afterReset = seen.dropWhile { it == before }
        assertTrue("the engine never published a reset", afterReset.isNotEmpty())
        val leaked = afterReset.flatMap { it.values.flatten() }.filter { it.startsWith("ru:") }.distinct()
        assertTrue("old-language text after the reset: $leaked", leaked.isEmpty())
        for (i in 0..9) assertEquals("de:c0p$i", h.text(0, i))
    }

    /**
     * DeepL is current and on screen (a stale key, or a fallback in the real repository). Saving a new
     * key must re-plan the screen only once the key is durable: a re-plan before it would resolve the
     * provider with the old (or no) key, and nothing would re-plan after the write since the provider
     * didn't change. The fake translates any provider, so a DeepL request is the observable re-plan.
     */
    @Test
    fun `saving a key retranslates the screen only after the key is stored`() = runTest(main.dispatcher) {
        val settings = FakeTranslatorSettings(current = TranslationProvider.DEEPL)
        val h = harness(book(20), settings = settings)
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 4)
        advanceUntilIdle()
        val visible = (0..4).map { "c0p$it" }
        fun deeplCounts() = h.repo.servedBy(TranslationProvider.DEEPL).let { d -> visible.map { v -> d.count { it == v } } }
        val before = deeplCounts()
        assertEquals("precondition", List(5) { 1 }, before)
        val write = CompletableDeferred<Unit>()
        settings.writeDelay = { write.await() }

        h.vm.configureProvider(TranslationProvider.DEEPL, "k", null)
        advanceUntilIdle()
        assertEquals("re-requested with DeepL before the key was stored", before, deeplCounts())

        write.complete(Unit)
        advanceUntilIdle()
        val after = deeplCounts()
        for (i in 0..4) assertTrue("c0p$i not re-requested with DeepL after the key write: $after", after[i] > before[i])
        assertEquals("ru:c0p0", h.text(0, 0))
    }

    /**
     * The dialog's old race: the provider was selected before its key was written, so the screen was
     * re-planned against a provider with no key. Nothing may be sent to the new provider until the
     * save (key, then selection) has returned.
     */
    @Test
    fun `saving a key for a new provider sends nothing to it until the key is stored`() = runTest(main.dispatcher) {
        val settings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE)
        val h = harness(book(20), settings = settings)
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 4)
        advanceUntilIdle()
        assertTrue("precondition", "c0p0" in h.repo.servedBy(TranslationProvider.QUICK_TRANSLATE))
        val write = CompletableDeferred<Unit>()
        settings.writeDelay = { write.await() }

        h.vm.configureProvider(TranslationProvider.DEEPL, "k", null)
        advanceUntilIdle()
        assertEquals("sent to DeepL before its key was stored", emptyList<String>(), h.repo.servedBy(TranslationProvider.DEEPL))

        write.complete(Unit)
        advanceUntilIdle()
        val deepl = h.repo.servedBy(TranslationProvider.DEEPL)
        for (i in 0..4) assertTrue("c0p$i not requested with DeepL after the key write: $deepl", "c0p$i" in deepl)
    }

    /**
     * Pins that the switch to ML Kit reaches the engine (observed failing when the config collector
     * doesn't notify the engine). The VM's own invalidate() after clear is not what makes this pass:
     * the provider change already resets the engine, so dropping it leaves this test green.
     */
    @Test
    fun `clearing the current provider's key switches to ML Kit and retranslates`() = runTest(main.dispatcher) {
        val settings = FakeTranslatorSettings(current = TranslationProvider.DEEPL, configured = setOf(TranslationProvider.DEEPL))
        val h = harness(book(20), settings = settings)
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 4)
        advanceUntilIdle()
        assertTrue("precondition", "c0p0" in h.repo.servedBy(TranslationProvider.DEEPL))

        h.vm.clearProvider(TranslationProvider.DEEPL)
        advanceUntilIdle()

        assertEquals(TranslationProvider.MLKIT, h.success().translatorProvider)
        val mlkit = h.repo.servedBy(TranslationProvider.MLKIT)
        for (i in 0..4) assertTrue("c0p$i not retranslated with ML Kit: $mlkit", "c0p$i" in mlkit)
    }

    @Test
    fun `selecting a provider reaches the engine`() = runTest(main.dispatcher) {
        val h = harness(book(20))
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 4)
        advanceUntilIdle()

        h.vm.selectProvider(TranslationProvider.AZURE)
        advanceUntilIdle()

        val azure = h.repo.servedBy(TranslationProvider.AZURE)
        for (i in 0..4) assertTrue("c0p$i not re-requested with Azure: $azure", "c0p$i" in azure)
    }

    /**
     * Activity recreation re-runs ReaderScreen's LaunchedEffect(filePath) → loadBook on the same VM.
     * A reload is asynchronous (parse on the real Dispatchers.IO), so this waits a bounded wall-clock
     * window for a second parse; without the guard it shows up well within it.
     */
    @Test
    fun `reloading the same book keeps its translations`() = runTest(main.dispatcher) {
        val h = harness(book(20))
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 4)
        advanceUntilIdle()
        assertEquals("precondition", "ru:c0p0", h.text(0, 0))
        val translations = h.success().chapterTranslations
        val calls = h.repo.calls.size

        h.vm.loadBook(h.uri)
        val reparsed = pollUntil(RELOAD_WINDOW_MS) { h.parser.parses.get() > 1 }

        assertFalse("the book was parsed again", reparsed)
        assertEquals(1, h.parser.parses.get())
        assertEquals(translations, h.success().chapterTranslations)
        assertEquals("nothing retranslated", calls, h.repo.calls.size)
    }

    @Test
    fun `earlier chapters keep their translations after a forward jump`()= runTest(main.dispatcher) {
        val h = harness(book(10, 10, 10))
        open(h)
        h.vm.onVisibleRange(0, 0, 0, 4)
        advanceUntilIdle()
        for (i in 0..4) assertEquals("precondition", "ru:c0p$i", h.text(0, i))

        h.vm.jumpToBookmark(2, 0)
        advanceUntilIdle()
        assertEquals("the jump moved the viewport", "ru:c2p0", h.text(2, 0))

        for (i in 0..4) assertEquals("ru:c0p$i", h.text(0, i))
    }

    private companion object {
        const val RELOAD_WINDOW_MS = 1_500L
    }
}
