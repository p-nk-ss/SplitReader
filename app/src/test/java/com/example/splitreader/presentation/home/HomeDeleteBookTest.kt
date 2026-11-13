package com.example.splitreader.presentation.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.DeleteBookUseCase
import com.example.splitreader.domain.usecase.GetStreakUseCase
import com.example.splitreader.domain.usecase.ParseBookUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Library's delete action removes the whole book, not only its row (see DeleteBookUseCaseTest). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeDeleteBookTest {

    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `deleting a book from the library forgets its position and deletes its files`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val uri = "content://books/x"
        val library = FakeBookLibraryRepository().apply { put(uri) }
        val prefs = FakeReadingPreferences().apply { saveReadingPosition(uri, ReadingPosition(3, 7, 42)) }
        val files = FakeBookFiles()
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
            deleteBookUseCase = DeleteBookUseCase(library, prefs, files),
            progressManager = prefs,
            sessionRepository = sessions,
            savedWordRepository = FakeSavedWordRepository(),
            getStreakUseCase = GetStreakUseCase(sessions),
            authRepository = SignedOutAuth,
        )

        vm.deleteBook(uri)

        assertFalse(library.exists(uri))
        assertEquals(ReadingPosition.START, prefs.getReadingPosition(uri))
        assertEquals(listOf(uri), files.deleted)
    }
}
