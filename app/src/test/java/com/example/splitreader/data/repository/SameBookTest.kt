package com.example.splitreader.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.local.AppDatabase
import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Note
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.AddResult
import com.example.splitreader.domain.usecase.DeleteBookUseCase
import com.example.splitreader.domain.usecase.SameBook
import com.example.splitreader.fakes.FakeBookContentKeys
import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeEntitlementRepository
import com.example.splitreader.fakes.FakeReadingPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Same book: DocumentsUI hands out a different uri for one file depending on where the reader
 * picked it (Recent -> media.documents, Downloads -> downloads.documents). The file's content is the
 * book's identity when a uri is new to the library: its reading data follows it to the new uri, and
 * it never becomes a second card.
 *
 * Real Room in memory with the production use cases, so the transaction and foreign keys are what run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SameBookTest {

    private lateinit var db: AppDatabase
    private lateinit var library: BookLibraryRepositoryImpl
    private lateinit var bookmarks: BookmarkRepositoryImpl
    private lateinit var notes: NoteRepositoryImpl
    private val prefs = FakeReadingPreferences()
    private val files = FakeBookFiles()
    private val keys = FakeBookContentKeys()
    private val entitlement = FakeEntitlementRepository()
    private lateinit var sameBook: SameBook
    private lateinit var add: AddBookToLibraryUseCase
    private lateinit var delete: DeleteBookUseCase

    private val recent = "content://com.android.providers.media.documents/document/document%3A1000"
    private val downloads = "content://com.android.providers.downloads.documents/document/msf%3A1000"

    private fun book(path: String, title: String = "T", cover: String? = null) = Book(
        title = title, author = "A", filePath = path, coverPath = cover,
        chapters = List(2) { Chapter(index = it, title = "C$it", paragraphs = listOf("p")) },
    )

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = BookLibraryRepositoryImpl(db.bookDao())
        bookmarks = BookmarkRepositoryImpl(db.bookmarkDao())
        notes = NoteRepositoryImpl(db.noteDao())
        sameBook = SameBook(library, keys, prefs, files, kotlinx.coroutines.Dispatchers.Unconfined)
        add = AddBookToLibraryUseCase(library, entitlement, sameBook)
        delete = DeleteBookUseCase(library, prefs, files)
        // One file, two uris.
        keys.keys[recent] = "moby"
        keys.keys[downloads] = "moby"
    }

    @After
    fun tearDown() = db.close()

    private fun rows(sql: String): List<String?> =
        db.openHelper.readableDatabase.query(sql).use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getString(0)) }
        }

    /** Adds the book under [recent] and gives it everything a reader leaves in a book. */
    private suspend fun readUnderRecent() {
        add.add(book(recent, cover = "/covers/recent.jpg"))
        bookmarks.add(recent, 1, 0)
        notes.upsert(Note(bookUri = recent, chapterIndex = 1, paragraphIndex = 0, body = "mine", isHighlight = false, createdAt = 1L, updatedAt = 1L))
        db.savedWordDao().insert(
            SavedWordEntity(
                word = "whale", sourceLang = "en", targetLang = "ru", translation = "кит",
                bookUri = recent, bookTitle = "T", chapterIndex = 1, paragraphIndex = 0, contextSnippet = "",
            )
        )
        db.readingSessionDao().insert(
            ReadingSessionEntity(
                bookUri = recent, bookTitle = "T", sourceLang = "en",
                startedAt = 0L, endedAt = 60_000L, durationSeconds = 60, paragraphsRead = 3,
            )
        )
        prefs.saveReadingPosition(recent, ReadingPosition(1, 0, 12))
        prefs.saveProgress(recent, 0.6f)
        prefs.saveSourceLanguage(recent, Language.GERMAN)
    }

    private suspend fun assertReadingDataUnder(uri: String) {
        assertEquals(listOf(1 to 0), bookmarks.listForBook(uri).map { it.chapterIndex to it.paragraphIndex })
        assertEquals(listOf("mine"), notes.observeForBook(uri).first().map { it.body })
        assertEquals(listOf<String?>(uri), rows("SELECT bookUri FROM saved_words"))
        assertEquals(listOf<String?>(uri), rows("SELECT bookUri FROM reading_sessions"))
        assertEquals(ReadingPosition(1, 0, 12), prefs.getReadingPosition(uri))
        assertEquals(0.6f, prefs.getProgress(uri))
        assertEquals(Language.GERMAN, prefs.getSourceLanguage(uri))
    }

    @Test
    fun `a removed book added again from another folder comes back with all its reading data`() = runTest {
        readUnderRecent()
        delete.delete(recent, alsoReadingData = false)

        assertEquals(AddResult.Added, add.add(book(downloads)))

        assertEquals(listOf(downloads), library.getAllBooks().first().map { it.uri })
        assertEquals(listOf<String?>(downloads), rows("SELECT uri FROM books"))
        assertReadingDataUnder(downloads)
        assertEquals(ReadingPosition.START, prefs.getReadingPosition(recent))
        assertNull(prefs.getProgress(recent))
    }

    @Test
    fun `a library book picked again from another folder is one card that keeps its data and needs no slot`() = runTest {
        readUnderRecent()
        add.add(book("/b/2"))
        add.add(book("/b/3"))
        assertEquals(AddBookToLibraryUseCase.FREE_BOOK_LIMIT, library.bookCount())

        assertEquals(AddResult.Added, add.add(book(downloads)))

        assertEquals(listOf(downloads, "/b/3", "/b/2"), library.getAllBooks().first().map { it.uri })
        assertReadingDataUnder(downloads)
        // The old uri's cover and pictures are named after it: they go, and its read grant with them.
        assertEquals(listOf(recent), files.deleted)
    }

    @Test
    fun `the moved book does not keep the cover path named after its old uri`() = runTest {
        readUnderRecent()

        add.add(book(downloads, cover = null)) // a re-parse whose cover write failed

        assertNull(library.getAllBooks().first().single().coverPath)
    }

    @Test
    fun `a removed book matched from another folder still needs a free slot`() = runTest {
        readUnderRecent()
        delete.delete(recent, alsoReadingData = false)
        add.add(book("/b/2"))
        add.add(book("/b/3"))
        add.add(book("/b/4"))

        assertEquals(AddResult.LimitReached, add.add(book(downloads)))

        // Nothing moved: the removed book still waits under its old uri.
        assertEquals(listOf<String?>(recent), rows("SELECT uri FROM books WHERE removedAt IS NOT NULL"))
        assertEquals(1, bookmarks.listForBook(recent).size)
        assertEquals(0.6f, prefs.getProgress(recent))

        entitlement.setPremium(true)
        assertEquals(AddResult.Added, add.add(book(downloads)))
        assertReadingDataUnder(downloads)
    }

    @Test
    fun `a different file is a new book`() = runTest {
        readUnderRecent()
        keys.keys["/b/other"] = "dick"

        assertEquals(AddResult.Added, add.add(book("/b/other")))

        assertEquals(listOf("/b/other", recent), library.getAllBooks().first().map { it.uri })
        assertEquals(1, bookmarks.listForBook(recent).size)
        assertEquals(emptyList<Any>(), bookmarks.listForBook("/b/other"))
        assertEquals(listOf<String?>("dick"), rows("SELECT contentKey FROM books WHERE uri = '/b/other'"))
    }

    @Test
    fun `a file that cannot be read is added as a new book without a key`() = runTest {
        readUnderRecent()
        keys.keys.remove(downloads)

        assertEquals(AddResult.Added, add.add(book(downloads)))

        assertEquals(listOf(downloads, recent), library.getAllBooks().first().map { it.uri })
        assertEquals(listOf<String?>(null), rows("SELECT contentKey FROM books WHERE uri = '$downloads'"))
        assertEquals(1, bookmarks.listForBook(recent).size)
    }

    @Test
    fun `re-adding a uri the library knows reads the file only to fill a missing key`() = runTest {
        add.add(book(recent))
        assertEquals(listOf<String?>("moby"), rows("SELECT contentKey FROM books WHERE uri = '$recent'"))
        keys.reads.clear()

        add.add(book(recent))
        assertEquals(emptyList<String>(), keys.reads)
        assertEquals("a re-save keeps the key", listOf<String?>("moby"), rows("SELECT contentKey FROM books WHERE uri = '$recent'"))

        // A book from before v29 has no key: the next add fills it.
        db.openHelper.writableDatabase.execSQL("UPDATE books SET contentKey = NULL")
        add.add(book(recent))
        assertEquals(listOf(recent), keys.reads)
        assertEquals(listOf<String?>("moby"), rows("SELECT contentKey FROM books WHERE uri = '$recent'"))
    }

    @Test
    fun `opening a library book without a key fills it, once`() = runTest {
        add.add(book(recent))
        db.openHelper.writableDatabase.execSQL("UPDATE books SET contentKey = NULL")
        keys.reads.clear()

        sameBook.rememberContent(recent)
        sameBook.rememberContent(recent)
        sameBook.rememberContent("/b/never-added")

        assertEquals(listOf(recent), keys.reads)
        assertEquals(listOf<String?>("moby"), rows("SELECT contentKey FROM books WHERE uri = '$recent'"))
    }

    @Test
    fun `a book whose key was filled on open is then found from another folder`() = runTest {
        readUnderRecent()
        db.openHelper.writableDatabase.execSQL("UPDATE books SET contentKey = NULL")
        sameBook.rememberContent(recent)
        delete.delete(recent, alsoReadingData = false)

        add.add(book(downloads))

        assertReadingDataUnder(downloads)
    }

    @Test
    fun `the same book is matched to the one in the library before a removed copy`() = runTest {
        // One file added twice before v29 (two rows, keys filled since): one removed, one in the library.
        readUnderRecent()
        add.add(book("/b/removed-copy"))
        delete.delete("/b/removed-copy", alsoReadingData = false)
        db.openHelper.writableDatabase.execSQL("UPDATE books SET contentKey = 'moby'")

        add.add(book(downloads))

        assertEquals(listOf(downloads), library.getAllBooks().first().map { it.uri })
        assertReadingDataUnder(downloads)
        assertTrue("the removed copy is left alone", "/b/removed-copy" in rows("SELECT uri FROM books"))
    }

    @Test
    fun `a book whose file now holds another book is not moved, and its key is renewed`() = runTest {
        readUnderRecent()
        keys.keys[recent] = "other" // the document under `recent` was overwritten with another book

        assertEquals(AddResult.Added, add.add(book(downloads)))

        assertEquals(listOf(downloads, recent), library.getAllBooks().first().map { it.uri })
        assertEquals(1, bookmarks.listForBook(recent).size)
        assertEquals(emptyList<Any>(), bookmarks.listForBook(downloads))
        assertEquals(listOf<String?>("other"), rows("SELECT contentKey FROM books WHERE uri = '$recent'"))
        assertEquals(listOf<String?>("moby"), rows("SELECT contentKey FROM books WHERE uri = '$downloads'"))
        assertEquals(emptyList<String>(), files.deleted)
    }

    @Test
    fun `a removed book whose file is gone is matched by its stored key`() = runTest {
        readUnderRecent()
        delete.delete(recent, alsoReadingData = false)
        keys.keys.remove(recent) // its old location can no longer be read

        add.add(book(downloads))

        assertReadingDataUnder(downloads)
    }

    @Test
    fun `the same file under another spelling of its path is not deleted with the old uri's files`() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("same-book")
        val real = java.nio.file.Files.createDirectory(dir.resolve("real"))
        val file = real.resolve("moby.epub").toFile().apply { writeText("moby") }
        val link = java.nio.file.Files.createSymbolicLink(dir.resolve("link"), real)
        val a = file.toURI().toString()
        val b = link.resolve("moby.epub").toUri().toString()
        keys.keys[a] = "moby"; keys.keys[b] = "moby"
        add.add(book(a))

        add.add(book(b))

        assertEquals(listOf(b), library.getAllBooks().first().map { it.uri })
        assertEquals("one file, two spellings: deleting the old uri's files would delete the book", emptyList<String>(), files.deleted)
    }
}
