package com.example.splitreader.presentation.home

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.repository.AuthRepository
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.DeleteBookUseCase
import com.example.splitreader.domain.usecase.GetStreakUseCase
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeBookParser
import com.example.splitreader.fakes.SignedOutAuth
import com.example.splitreader.fakes.FakeCrashReporter
import com.example.splitreader.fakes.FakeEntitlementRepository
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeReadingSessionRepository
import com.example.splitreader.fakes.FakeSavedWordRepository
import com.example.splitreader.presentation.reader.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Importing a book must not flash "Your library is empty" between the parse and the reader. The
 * home screen shows its loading skeleton while [HomeUiState.isLoading]; it used to drop that flag as
 * soon as parsing finished, while the book was still being saved, so for that moment the screen
 * rendered an empty library (found on tablet emulators, 2026-11-03).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeImportLoadingTest {

    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    /** A library whose save waits for [saveGate], so the test can look at the screen mid-save. */
    private class GatedLibrary : BookLibraryRepository {
        val saveGate = CompletableDeferred<Unit>()
        val saveStarted = CompletableDeferred<Unit>()
        val books = MutableStateFlow<List<LibraryBook>>(emptyList())
        override suspend fun saveBook(book: Book) {
            saveStarted.complete(Unit)
            saveGate.await()
        }
        override fun getAllBooks(): Flow<List<LibraryBook>> = books
        override suspend fun touchBook(uri: String) = Unit
        override suspend fun deleteBook(uri: String) = Unit
        override suspend fun bookCount(): Int = 0
        override suspend fun exists(uri: String): Boolean = false
    }

    private fun libraryBook(uri: String) = LibraryBook(
        uri = uri, title = "T", author = "A", coverPath = null, lastOpenedAt = 0L, chapterCount = 0, synopsis = null,
    )

    @Test
    fun `the screen stays in loading until the imported book is saved`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.cacheDir, "import.epub").apply { writeBytes(ByteArray(64)) }
        val library = GatedLibrary()
        val sessions = FakeReadingSessionRepository()
        val vm = HomeViewModel(
            context = context,
            parseBookUseCase = ParseBookUseCase(
                setOf(FakeBookParser(Book(title = "T", author = "A", chapters = emptyList(), filePath = ""))),
                context,
                FakeCrashReporter(),
            ),
            bookLibraryRepository = library,
            addBookToLibraryUseCase = AddBookToLibraryUseCase(library, FakeEntitlementRepository()),
            deleteBookUseCase = DeleteBookUseCase(library, FakeReadingPreferences(), FakeBookFiles()),
            progressManager = FakeReadingPreferences(),
            sessionRepository = sessions,
            savedWordRepository = FakeSavedWordRepository(),
            getStreakUseCase = GetStreakUseCase(sessions),
            authRepository = SignedOutAuth,
        )
        val collector = backgroundScope.launch { vm.uiState.collect {} }

        vm.openBook(Uri.fromFile(file))
        awaitReal { library.saveStarted.isCompleted }
        // uiState is combined on Dispatchers.Default and lags the VM's own flags. Wait for loading to
        // show, then give a premature reset time to land before reading what the screen shows.
        awaitReal { vm.uiState.value.isLoading }
        settle()

        assertTrue(
            "The book is still being saved, so the library is still empty: the screen must keep " +
                "its loading state, or it shows \"Your library is empty\" for that moment.",
            vm.uiState.value.isLoading,
        )

        // Saved, but Room re-queries the library asynchronously: until the book is in the list the
        // screen would still render an empty library (the reader fades in over it).
        library.saveGate.complete(Unit)
        settle()
        assertTrue(
            "The book is saved but not in the library list yet: the screen must stay in loading.",
            vm.uiState.value.isLoading,
        )

        library.books.value = listOf(libraryBook(Uri.fromFile(file).toString()))
        awaitReal { !vm.uiState.value.isLoading }
        assertFalse(vm.uiState.value.isLoading)
        collector.cancel()
    }

    private fun TestScope.settle() {
        val until = System.nanoTime() + 500_000_000L
        while (System.nanoTime() < until) {
            runCurrent()
            Thread.sleep(10)
        }
    }

    // runCurrent, not advanceUntilIdle: the VM bounds its wait for the library with a timeout, and
    // advancing virtual time would fire it at once.
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
