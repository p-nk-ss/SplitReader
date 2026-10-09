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
import java.util.Collections
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        prefs: FakeReadingPreferences = FakeReadingPreferences(provider = TranslationProvider.QUICK_TRANSLATE),
        repo: FakeTranslationRepository = FakeTranslationRepository(),
        legacy: FakeLegacyReadingPositionStore = FakeLegacyReadingPositionStore(),
    ) = ReaderViewModelHarness(book, prefs = prefs, repo = repo, legacy = legacy, ioDispatcher = main.dispatcher)

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

    @Test
    fun `earlier chapters keep their translations after a forward jump`() = runTest(main.dispatcher) {
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
}
