package com.example.splitreader.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.local.AppDatabase
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
    fun `re-saving a book still updates its metadata`() = runTest {
        library.saveBook(book(title = "Renamed", chapters = 5))
        val saved = library.getAllBooks().first().single()
        assertEquals("Renamed", saved.title)
        assertEquals(5, saved.chapterCount)
    }
}
