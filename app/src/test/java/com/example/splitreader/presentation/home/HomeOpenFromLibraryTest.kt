package com.example.splitreader.presentation.home

import com.example.splitreader.fakes.sameBookFor
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.ReadingPosition
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
import com.example.splitreader.presentation.reader.MainDispatcherRule
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opening a book from the Library gives a book added before v29 its content key (see SameBook), so
 * it is recognised when it is next added from another folder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeOpenFromLibraryTest {

    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val uri = "file:///data/user/0/app/files/catalog/gutenberg-1.epub"
    private val library = FakeBookLibraryRepository().apply { put(uri) }
    private val prefs = FakeReadingPreferences().apply { saveReadingPosition(uri, ReadingPosition(3, 7, 42)) }
    private val files = FakeBookFiles()
    private val keys = FakeBookContentKeys(mutableMapOf(uri to "moby"))

    private fun viewModel(): HomeViewModel {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sessions = FakeReadingSessionRepository()
        return HomeViewModel(
            context = context,
            parseBookUseCase = ParseBookUseCase(
                setOf(FakeBookParser(Book(title = "T", author = "A", chapters = emptyList(), filePath = ""))),
                context,
                FakeCrashReporter(),
            ),
            bookLibraryRepository = library,
            addBookToLibraryUseCase = AddBookToLibraryUseCase(library, FakeEntitlementRepository(), sameBookFor(library, keys)),
            deleteBookUseCase = DeleteBookUseCase(library, prefs, files),
            progressManager = prefs,
            sessionRepository = sessions,
            savedWordRepository = FakeSavedWordRepository(),
            getStreakUseCase = GetStreakUseCase(sessions),
            authRepository = SignedOutAuth,
        )
    }

    @Test
    fun `opening a library book without a key gives it one`() = runTest(main.dispatcher) {
        viewModel().openBookFromLibrary(uri)

        assertEquals("moby", library.contentKeys[uri])
    }

    @Test
    fun `opening a library book that has a key reads no file`() = runTest(main.dispatcher) {
        library.contentKeys[uri] = "moby"

        viewModel().openBookFromLibrary(uri)

        assertEquals(emptyList<String>(), keys.reads)
    }
}
