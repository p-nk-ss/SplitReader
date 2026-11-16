package com.example.splitreader.presentation.home

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.DeleteBookUseCase
import com.example.splitreader.domain.usecase.GetStreakUseCase
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.example.splitreader.fakes.FakeBookContentKeys
import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeBookLibraryRepository
import com.example.splitreader.fakes.FakeBookParser
import com.example.splitreader.fakes.FakeCrashReporter
import com.example.splitreader.fakes.FakeEntitlementRepository
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeReadingSessionRepository
import com.example.splitreader.fakes.FakeSavedWordRepository
import com.example.splitreader.fakes.SignedOutAuth
import com.example.splitreader.fakes.sameBookFor
import com.example.splitreader.presentation.reader.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A free reader with a full library picks one of their books again from another folder: it is the
 * Same book, a re-open, so the import's pre-check (before parsing) must let it through instead of
 * offering the upgrade -- and a different file is still refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSameBookAtLimitTest {

    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val recent = "content://com.android.providers.media.documents/document/document%3A1000"
    private val library = FakeBookLibraryRepository().apply {
        put(recent); put("/b/2"); put("/b/3")
        contentKeys[recent] = "moby"
    }
    private val file = File(context.cacheDir, "moby.epub").apply { writeBytes(ByteArray(64)) }
    private val picked = Uri.fromFile(file)
    private val keys = FakeBookContentKeys(mutableMapOf(recent to "moby"))

    private fun viewModel(): HomeViewModel {
        val sessions = FakeReadingSessionRepository()
        val prefs = FakeReadingPreferences()
        return HomeViewModel(
            context = context,
            parseBookUseCase = ParseBookUseCase(
                setOf(FakeBookParser(Book(title = "T", author = "A", chapters = emptyList(), filePath = ""))),
                context,
                FakeCrashReporter(),
            ),
            bookLibraryRepository = library,
            addBookToLibraryUseCase = AddBookToLibraryUseCase(
                library, FakeEntitlementRepository(), sameBookFor(library, keys, prefs),
            ),
            deleteBookUseCase = DeleteBookUseCase(library, prefs, FakeBookFiles()),
            progressManager = prefs,
            sessionRepository = sessions,
            savedWordRepository = FakeSavedWordRepository(),
            getStreakUseCase = GetStreakUseCase(sessions),
            authRepository = SignedOutAuth,
        )
    }

    @Test
    fun `at the limit, a library book picked from another folder opens instead of the upgrade`() = runTest(main.dispatcher) {
        keys.keys[picked.toString()] = "moby"
        val vm = viewModel()
        var limitDialogs = 0
        val opened = mutableListOf<String>()
        backgroundScope.launch { vm.limitReachedEvent.collect { limitDialogs++ } }
        backgroundScope.launch { vm.navigationEvent.collect { opened += it } }

        vm.openBook(picked)
        awaitReal { opened.isNotEmpty() || limitDialogs > 0 }

        assertEquals(0, limitDialogs)
        assertEquals(listOf(picked.toString()), opened)
        assertEquals(3, library.books.value.size)
        assertEquals(setOf(picked.toString(), "/b/2", "/b/3"), library.books.value.map { it.uri }.toSet())
    }

    @Test
    fun `at the limit, a different file still gets the upgrade`() = runTest(main.dispatcher) {
        keys.keys[picked.toString()] = "dick"
        val vm = viewModel()
        var limitDialogs = 0
        backgroundScope.launch { vm.limitReachedEvent.collect { limitDialogs++ } }

        vm.openBook(picked)
        awaitReal { limitDialogs > 0 }

        assertEquals(3, library.books.value.size)
    }

    /** Parsing runs on the real Dispatchers.IO, which virtual time cannot drive. */
    private fun TestScope.awaitReal(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            runCurrent()
            if (condition()) return
            Thread.sleep(10)
        }
        error("Timed out")
    }
}
