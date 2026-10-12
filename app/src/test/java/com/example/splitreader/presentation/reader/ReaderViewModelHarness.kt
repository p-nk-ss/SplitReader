package com.example.splitreader.presentation.reader

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.usecase.EndReadingSessionUseCase
import com.example.splitreader.domain.usecase.MigrateLegacyReadingPositionsUseCase
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.example.splitreader.domain.usecase.SaveWordUseCase
import com.example.splitreader.domain.usecase.ToggleBookmarkUseCase
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import com.example.splitreader.fakes.FakeBookParser
import com.example.splitreader.fakes.FakeBookmarkRepository
import com.example.splitreader.fakes.FakeCrashReporter
import com.example.splitreader.fakes.FakeLanguageDetector
import com.example.splitreader.fakes.FakeLegacyReadingPositionStore
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeReadingSessionRepository
import com.example.splitreader.fakes.FakeSavedWordRepository
import com.example.splitreader.fakes.FakeSpeechSynthesizer
import com.example.splitreader.fakes.FakeTranslationRepository
import com.example.splitreader.fakes.FakeTranslatorSettings
import com.example.splitreader.domain.model.TranslationProvider
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.fail
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Builds a real ReaderViewModel over fakes. Run under Robolectric with Dispatchers.Main set to the test dispatcher. */
internal class ReaderViewModelHarness(
    val book: Book,
    val prefs: FakeReadingPreferences = FakeReadingPreferences(),
    val repo: FakeTranslationRepository = FakeTranslationRepository(),
    val legacy: FakeLegacyReadingPositionStore = FakeLegacyReadingPositionStore(),
    val crash: FakeCrashReporter = FakeCrashReporter(),
    val sessions: FakeReadingSessionRepository = FakeReadingSessionRepository(),
    val settings: FakeTranslatorSettings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE),
    ioDispatcher: CoroutineDispatcher,
) {
    val bookmarks = FakeBookmarkRepository()
    private val context: Application = ApplicationProvider.getApplicationContext()
    val uri: Uri = File(context.cacheDir, "book.epub").apply { writeText("PK") }.toUri()

    private val translate = TranslateTextUseCase(repo, ioDispatcher)

    val parser = FakeBookParser(book)

    val savedWords = FakeSavedWordRepository()

    val vm = ReaderViewModel(
        translateTextUseCase = translate,
        parseBookUseCase = ParseBookUseCase(setOf(parser), context, crash),
        saveWordUseCase = SaveWordUseCase(savedWords),
        toggleBookmarkUseCase = ToggleBookmarkUseCase(bookmarks),
        bookmarkRepository = bookmarks,
        endReadingSessionUseCase = EndReadingSessionUseCase(sessions),
        migrateLegacyReadingPositions = MigrateLegacyReadingPositionsUseCase(legacy, bookmarks),
        crashReporter = crash,
        progressManager = prefs,
        languageDetector = FakeLanguageDetector(),
        translatorSettings = settings,
        textToSpeechManager = FakeSpeechSynthesizer(),
        context = context,
    )
}

/** Sets Dispatchers.Main to [dispatcher] for the test, so viewModelScope runs on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * Waits for the book to open. The VM hops to the real Dispatchers.IO (parsing, migration), which virtual time cannot drive, so this alternates
 * draining the test dispatcher with short real sleeps, bounded by a 10 s wall-clock deadline
 * (a cold Robolectric start alone takes ~2 s on a fast machine).
 */
internal suspend fun TestScope.awaitLoaded(h: ReaderViewModelHarness): ReaderUiState.Success {
    val deadline = System.nanoTime() + LOAD_TIMEOUT_NANOS
    while (System.nanoTime() < deadline) {
        advanceUntilIdle()
        (h.vm.uiState.value as? ReaderUiState.Success)?.let { return it }
        Thread.sleep(10)
    }
    fail("book never loaded; last state: ${h.vm.uiState.value}")
    error("unreachable")
}

/**
 * Drains the test dispatcher with short real sleeps (for work the VM hops to a real dispatcher)
 * until [condition] holds or [timeoutMillis] of wall-clock time pass; returns whether it held.
 */
internal fun TestScope.pollUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (System.nanoTime() < deadline) {
        advanceUntilIdle()
        if (condition()) return true
        Thread.sleep(10)
    }
    advanceUntilIdle()
    return condition()
}

private const val LOAD_TIMEOUT_NANOS = 10_000_000_000L
