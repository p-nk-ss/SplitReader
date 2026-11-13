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
 * Deleting a book used to remove only its library row: its position, progress, excerpt and
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
    fun `deleting a book removes its row, everything remembered about it and its files`() = runTest {
        useCase.delete(uri)

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
        useCase.delete(uri)

        assertTrue(library.exists(other))
        assertEquals(ReadingPosition(3, 7, 42), prefs.getReadingPosition(other))
        assertTrue(prefs.isFinished(other))
        assertEquals(listOf(uri), files.deleted)
    }

    @Test
    fun `the row goes first, so a failed delete leaves the book whole`() = runTest {
        library.failDelete = IllegalStateException("db")

        val thrown = runCatching { useCase.delete(uri) }.exceptionOrNull()

        assertEquals("db", thrown?.message)
        assertEquals(ReadingPosition(3, 7, 42), prefs.getReadingPosition(uri))
        assertEquals(emptyList<String>(), files.deleted)
    }

    @Test
    fun `files that cannot be removed do not fail the delete`() = runTest {
        files.fail = java.io.IOException("disk")

        useCase.delete(uri)

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

        launch(job) { useCase.delete(uri) }.join()

        assertEquals(listOf("row $uri", "files $uri"), log)
        assertEquals(listOf(uri), prefs.forgotten)
    }
}
