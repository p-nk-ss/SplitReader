package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeBookLibraryRepository
import com.example.splitreader.fakes.FakeReadingPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A delete keeps what the reader made in the book -- place, progress, bookmarks, notes -- unless the
 * reader asks to delete that too; the book leaves the library and its files go either way.
 *
 * History: deleting a book used to remove only its library row: its position, progress, excerpt and
 * "finished" mark stayed in the preferences (the same file imported again opened mid-book, marked
 * finished) and its cover, pictures and downloaded file stayed on disk.
 */
class DeleteBookUseCaseTest {

    private val uri = "content://books/x"
    private val other = "content://books/y"
    private val log = mutableListOf<String>()
    private val library = FakeBookLibraryRepository(log).apply { put(uri); put(other) }
    private val prefs = FakeReadingPreferences().apply {
        for (u in listOf(uri, other)) {
            saveReadingPosition(u, ReadingPosition(3, 7, 42))
            saveProgress(u, 0.5f)
            saveExcerpt(u, "excerpt")
            markFinished(u)
            saveSourceLanguage(u, Language.GERMAN)
        }
    }
    private val files = FakeBookFiles(log)
    private val useCase = DeleteBookUseCase(library, prefs, files)

    @Test
    fun `a delete keeps the reader's place, progress and chosen language, and still removes the files`() = runTest {
        useCase.delete(uri, alsoReadingData = false)

        assertFalse(library.exists(uri))
        assertEquals(ReadingPosition(3, 7, 42), prefs.getReadingPosition(uri))
        assertEquals(0.5f, prefs.getProgress(uri))
        assertEquals("excerpt", prefs.getExcerpt(uri))
        assertTrue(prefs.isFinished(uri))
        assertEquals(Language.GERMAN, prefs.getSourceLanguage(uri))
        assertEquals(emptyList<String>(), prefs.forgotten)
        assertEquals(listOf(uri), files.deleted)
    }

    @Test
    fun `a delete that keeps the reading data hides the row instead of deleting it`() = runTest {
        useCase.delete(uri, alsoReadingData = false)

        assertEquals(listOf("hide $uri", "files $uri"), log)
        assertEquals(setOf(uri), library.removed)
    }

    @Test
    fun `a failed remove leaves the book whole`() = runTest {
        library.failDelete = IllegalStateException("db")

        val thrown = runCatching { useCase.delete(uri, alsoReadingData = false) }.exceptionOrNull()

        assertEquals("db", thrown?.message)
        assertTrue(library.exists(uri))
        assertEquals(emptyList<String>(), files.deleted)
    }

    @Test
    fun `deleting a book with its reading data removes its row, everything remembered about it and its files`() = runTest {
        useCase.delete(uri, alsoReadingData = true)

        assertFalse(library.exists(uri))
        assertEquals(ReadingPosition.START, prefs.getReadingPosition(uri))
        assertNull(prefs.getProgress(uri))
        assertNull(prefs.getExcerpt(uri))
        assertFalse(prefs.isFinished(uri))
        assertNull(prefs.getSourceLanguage(uri))
        assertEquals(listOf(uri), files.deleted)
    }

    @Test
    fun `another book is untouched`() = runTest {
        useCase.delete(uri, alsoReadingData = true)

        assertTrue(library.exists(other))
        assertEquals(ReadingPosition(3, 7, 42), prefs.getReadingPosition(other))
        assertTrue(prefs.isFinished(other))
        assertEquals(listOf(uri), files.deleted)
    }

    @Test
    fun `the row goes first, so a failed delete leaves the book whole`() = runTest {
        library.failDelete = IllegalStateException("db")

        val thrown = runCatching { useCase.delete(uri, alsoReadingData = true) }.exceptionOrNull()

        assertEquals("db", thrown?.message)
        assertEquals(ReadingPosition(3, 7, 42), prefs.getReadingPosition(uri))
        assertEquals(emptyList<String>(), files.deleted)
    }

    @Test
    fun `files that cannot be removed do not fail the delete`() = runTest {
        files.fail = java.io.IOException("disk")

        useCase.delete(uri, alsoReadingData = true)

        assertFalse(library.exists(uri))
        assertEquals(listOf(uri), prefs.forgotten)
    }

    @Test
    fun `a delete that has started finishes even when its caller is cancelled`() = runTest {
        val job = Job()
        // Cancels the caller from inside the first step: the rest must still run.
        val cancelling = FakeBookLibraryRepository(log).apply { put(uri) }
        val cancelOnForget = object : com.example.splitreader.domain.repository.BookFiles {
            override suspend fun deleteFor(bookUri: String) { log += "files $bookUri" }
        }
        val prefs = FakeReadingPreferences()
        val useCase = DeleteBookUseCase(
            object : com.example.splitreader.domain.repository.BookLibraryRepository by cancelling {
                override suspend fun deleteBook(uri: String) {
                    cancelling.deleteBook(uri)
                    job.cancel()
                }
            },
            prefs, cancelOnForget,
        )

        launch(job) { useCase.delete(uri, alsoReadingData = true) }.join()

        assertEquals(listOf("row $uri", "files $uri"), log)
        assertEquals(listOf(uri), prefs.forgotten)
    }
}
