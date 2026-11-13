package com.example.splitreader.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.local.AppDatabase
import com.example.splitreader.data.local.BookDao
import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Saving a book that is already in the library (re-import of the same file, a catalog or Drive
 * re-download) must update its row in place. Every child table references `books.uri`: bookmarks
 * and notes with ON DELETE CASCADE, saved words and reading sessions with ON DELETE SET NULL. A
 * save that deletes and re-inserts the row (SQLite `INSERT OR REPLACE`) fires those actions and
 * silently wipes or orphans the reader's data.
 *
 * Real Room in memory, so the production foreign keys and conflict strategy are what run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BookLibraryRepositoryResaveTest {

    private lateinit var db: AppDatabase
    private lateinit var library: BookLibraryRepositoryImpl
    private lateinit var bookmarks: BookmarkRepositoryImpl

    private val uri = "/b/x"
    private fun book(title: String = "T", chapters: Int = 2) = Book(
        title = title, author = "A", filePath = uri,
        chapters = List(chapters) { Chapter(index = it, title = "C$it", paragraphs = listOf("p")) },
    )

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = BookLibraryRepositoryImpl(db.bookDao())
        bookmarks = BookmarkRepositoryImpl(db.bookmarkDao())

        library.saveBook(book())
        bookmarks.add(uri, 0, 0)
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
    }

    @After
    fun tearDown() = db.close()

    private fun bookUris(table: String): List<String?> =
        db.openHelper.readableDatabase.query("SELECT bookUri FROM $table").use { c ->
            buildList { while (c.moveToNext()) add(if (c.isNull(0)) null else c.getString(0)) }
        }

    @Test
    fun `re-saving a book keeps its bookmarks`() = runTest {
        library.saveBook(book())
        assertEquals(1, bookmarks.listForBook(uri).size)
    }

    @Test
    fun `re-saving a book keeps saved words and reading sessions attached to it`() = runTest {
        library.saveBook(book())
        assertEquals(listOf<String?>(uri), bookUris("saved_words"))
        assertEquals(listOf<String?>(uri), bookUris("reading_sessions"))
    }

    @Test
    fun `deleting a book deletes its bookmarks and keeps its saved words and reading history, detached`() = runTest {
        library.deleteBook(uri)
        assertEquals(emptyList<String?>(), bookUris("bookmarks"))
        assertEquals(listOf<String?>(null), bookUris("saved_words"))
        assertEquals(listOf<String?>(null), bookUris("reading_sessions"))
    }

    @Test
    fun `re-saving a book still updates its metadata`() = runTest {
        library.saveBook(book(title = "Renamed", chapters = 5))
        val saved = library.getAllBooks().first().single()
        assertEquals("Renamed", saved.title)
        assertEquals(5, saved.chapterCount)
    }

    @Test
    fun `re-saving a book with no cover keeps the stored cover`() = runTest {
        library.saveBook(book().copy(coverPath = "/covers/x.jpg"))
        library.saveBook(book().copy(coverPath = null))
        assertEquals("/covers/x.jpg", library.getAllBooks().first().single().coverPath)
    }

    @Test
    fun `re-saving a book with a new cover replaces the stored cover`() = runTest {
        library.saveBook(book().copy(coverPath = "/covers/x.jpg"))
        library.saveBook(book().copy(coverPath = "/covers/y.jpg"))
        assertEquals("/covers/y.jpg", library.getAllBooks().first().single().coverPath)
    }

    /**
     * The stored cover is read and the row written by ONE DAO call (a Room transaction). As two
     * calls, a removal could land between them: here it does, deterministically, whenever the
     * repository reads the cover on its own -- and the save then brought the book back with the
     * cover path of the file that removal deleted.
     */
    @Test
    fun `a removal can not land between reading the stored cover and saving the book`() = runTest {
        library.saveBook(book().copy(coverPath = "/covers/x.jpg"))
        val real = db.bookDao()
        var separateReads = 0
        val dao = object : BookDao by real {
            override suspend fun coverPathOf(uri: String): String? =
                real.coverPathOf(uri).also {
                    separateReads++
                    real.markRemoved(uri, 1L) // the removal, right after the read
                }
        }

        BookLibraryRepositoryImpl(dao).saveBook(book().copy(coverPath = null))

        assertEquals("the cover was read outside the saving transaction", 0, separateReads)
    }
}
