package com.example.splitreader.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.local.AppDatabase
import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Note
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase.Companion.FREE_BOOK_LIMIT
import com.example.splitreader.domain.usecase.AddResult
import com.example.splitreader.domain.usecase.DeleteBookUseCase
import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeEntitlementRepository
import com.example.splitreader.fakes.FakeReadingPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A deleted book leaves the library and frees its free-tier slot, but -- unless the reader asked to
 * delete their reading data too -- its place, bookmarks and notes wait for the same book to be added
 * again. Bookmarks and notes hang off the book row (ON DELETE CASCADE), so the row stays, hidden.
 *
 * Real Room in memory with the production use cases, so the queries and foreign keys are what run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RemovedBookTest {

    private lateinit var db: AppDatabase
    private lateinit var library: BookLibraryRepositoryImpl
    private lateinit var bookmarks: BookmarkRepositoryImpl
    private lateinit var notes: NoteRepositoryImpl
    private val prefs = FakeReadingPreferences()
    private val files = FakeBookFiles()
    private val entitlement = FakeEntitlementRepository()
    private lateinit var delete: DeleteBookUseCase
    private lateinit var add: AddBookToLibraryUseCase

    private val uri = "file:///data/user/0/app/files/catalog/gutenberg-1.epub"
    private fun book(path: String = uri, title: String = "T", cover: String? = null) = Book(
        title = title, author = "A", filePath = path, coverPath = cover,
        chapters = List(2) { Chapter(index = it, title = "C$it", paragraphs = listOf("p")) },
    )
    private fun note() = Note(
        bookUri = uri, chapterIndex = 1, paragraphIndex = 0, body = "mine", isHighlight = false, createdAt = 1L, updatedAt = 1L,
    )

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = BookLibraryRepositoryImpl(db.bookDao())
        bookmarks = BookmarkRepositoryImpl(db.bookmarkDao())
        notes = NoteRepositoryImpl(db.noteDao())
        delete = DeleteBookUseCase(library, prefs, files)
        add = AddBookToLibraryUseCase(library, entitlement)

        add.add(book(cover = "/covers/old.jpg"))
        bookmarks.add(uri, 1, 0)
        notes.upsert(note())
        prefs.saveReadingPosition(uri, ReadingPosition(1, 0, 12))
        prefs.saveProgress(uri, 0.6f)
    }

    @After
    fun tearDown() = db.close()

    private fun rows(sql: String): List<String?> =
        db.openHelper.readableDatabase.query(sql).use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getString(0)) }
        }

    @Test
    fun `a deleted book is in no library list, count or owned check`() = runTest {
        delete.delete(uri, alsoReadingData = false)

        assertEquals(emptyList<String>(), library.getAllBooks().first().map { it.uri })
        assertEquals(0, library.bookCount())
        assertFalse(library.exists(uri))
    }

    @Test
    fun `its bookmarks, notes, place and progress come back when the same book is added again`() = runTest {
        delete.delete(uri, alsoReadingData = false)

        assertEquals(AddResult.Added, add.add(book()))

        assertEquals(listOf(uri), library.getAllBooks().first().map { it.uri })
        assertEquals(listOf(1 to 0), bookmarks.listForBook(uri).map { it.chapterIndex to it.paragraphIndex })
        assertEquals(listOf("mine"), notes.observeForBook(uri).first().map { it.body })
        assertEquals(ReadingPosition(1, 0, 12), prefs.getReadingPosition(uri))
        assertEquals(0.6f, prefs.getProgress(uri))
    }

    @Test
    fun `its files are removed whether or not the reading data is kept`() = runTest {
        delete.delete(uri, alsoReadingData = false)
        assertEquals(listOf(uri), files.deleted)
    }

    @Test
    fun `a book added again does not get back the cover file that was deleted with it`() = runTest {
        delete.delete(uri, alsoReadingData = false)

        // A re-parse that came back without a cover: "keep the stored one" must not resurrect a path
        // whose file the delete removed.
        add.add(book(cover = null))
        assertNull(library.getAllBooks().first().single().coverPath)

        add.add(book(cover = "/covers/new.jpg"))
        assertEquals("/covers/new.jpg", library.getAllBooks().first().single().coverPath)
    }

    @Test
    fun `a book added again takes the fresh title and moves to the top`() = runTest {
        add.add(book(path = "/b/other"))
        delete.delete(uri, alsoReadingData = false)

        add.add(book(title = "Renamed"))

        val listed = library.getAllBooks().first()
        assertEquals(listOf(uri, "/b/other"), listed.map { it.uri })
        assertEquals("Renamed", listed.first().title)
    }

    @Test
    fun `deleting a book frees its free-tier slot`() = runTest {
        add.add(book(path = "/b/2"))
        add.add(book(path = "/b/3"))
        assertEquals(FREE_BOOK_LIMIT, library.bookCount())
        assertEquals(AddResult.LimitReached, add.add(book(path = "/b/4")))

        delete.delete(uri, alsoReadingData = false)

        assertTrue(add.canAddNew("/b/4"))
        assertEquals(AddResult.Added, add.add(book(path = "/b/4")))
        assertEquals(FREE_BOOK_LIMIT, library.bookCount())
    }

    @Test
    fun `adding a deleted book again needs a free slot -- it is not an owned book`() = runTest {
        add.add(book(path = "/b/2"))
        add.add(book(path = "/b/3"))
        delete.delete(uri, alsoReadingData = false)
        add.add(book(path = "/b/4"))

        // The Catalog/Drive gate asks this before downloading (localUriFor -> canAddNew).
        assertFalse(add.canAddNew(uri))
        assertEquals(AddResult.LimitReached, add.add(book()))
        assertFalse(library.exists(uri))
        assertEquals(FREE_BOOK_LIMIT, library.bookCount())
    }

    @Test
    fun `premium adds a deleted book again past the limit, with its bookmarks`() = runTest {
        add.add(book(path = "/b/2"))
        add.add(book(path = "/b/3"))
        delete.delete(uri, alsoReadingData = false)
        add.add(book(path = "/b/4"))
        entitlement.setPremium(true)

        assertEquals(AddResult.Added, add.add(book()))

        assertEquals(4, library.bookCount())
        assertEquals(1, bookmarks.listForBook(uri).size)
    }

    @Test
    fun `saved words and reading sessions are never deleted`() = runTest {
        db.savedWordDao().insert(
            SavedWordEntity(
                word = "w", sourceLang = "en", targetLang = "ru", translation = "t",
                bookUri = uri, bookTitle = "T", chapterIndex = 0, paragraphIndex = 0, contextSnippet = "",
            )
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity(
                bookUri = uri, bookTitle = "T", sourceLang = "en",
                startedAt = 0L, endedAt = 60_000L, durationSeconds = 60, paragraphsRead = 3,
            )
        )

        delete.delete(uri, alsoReadingData = false)
        assertEquals(listOf<String?>("w"), rows("SELECT word FROM saved_words"))
        assertEquals(listOf<String?>("60"), rows("SELECT durationSeconds FROM reading_sessions"))

        add.add(book())
        delete.delete(uri, alsoReadingData = true)
        assertEquals(listOf<String?>("w"), rows("SELECT word FROM saved_words"))
        assertEquals(listOf<String?>("60"), rows("SELECT durationSeconds FROM reading_sessions"))
    }

    @Test
    fun `deleting with the reading data deletes the row, bookmarks, notes, place and progress`() = runTest {
        delete.delete(uri, alsoReadingData = true)

        assertEquals(emptyList<String?>(), rows("SELECT uri FROM books"))
        assertEquals(emptyList<String?>(), rows("SELECT bookUri FROM bookmarks"))
        assertEquals(emptyList<String?>(), rows("SELECT bookUri FROM notes"))
        assertEquals(ReadingPosition.START, prefs.getReadingPosition(uri))
        assertNull(prefs.getProgress(uri))
        assertEquals(listOf(uri), files.deleted)

        add.add(book())
        assertEquals(emptyList<Any>(), bookmarks.listForBook(uri))
    }

    @Test
    fun `a book deleted first without and then with its reading data leaves nothing behind`() = runTest {
        delete.delete(uri, alsoReadingData = false)
        assertEquals(listOf<String?>(uri), rows("SELECT uri FROM books"))

        add.add(book())
        delete.delete(uri, alsoReadingData = true)

        assertEquals(emptyList<String?>(), rows("SELECT uri FROM books"))
        assertEquals(emptyList<String?>(), rows("SELECT bookUri FROM bookmarks"))
    }

    @Test
    fun `opening a removed book's row by a stale touch does not bring it back`() = runTest {
        delete.delete(uri, alsoReadingData = false)
        library.touchBook(uri)
        assertFalse(library.exists(uri))
        assertEquals(0, library.bookCount())
    }
}
