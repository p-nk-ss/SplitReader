package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.fakes.FakeBookContentKeys
import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeBookLibraryRepository
import com.example.splitreader.fakes.FakeEntitlementRepository
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.sameBookFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

/**
 * How [SameBook] moves a book to a new uri, in-memory: the order of the prefs and library steps (a
 * process death between any two must leave the reading data under a uri that has a row), the thread
 * the prefs' synchronous disk writes run on, and two adds of one file at once.
 */
class SameBookMoveTest {

    private val recent = "content://media/document%3A1000"
    private val downloads = "content://downloads/msf%3A1000"
    private val log = mutableListOf<String>()
    private val library = FakeBookLibraryRepository(log = log).apply {
        put(recent); put("/b/2"); put("/b/3")
        contentKeys[recent] = "moby"
    }
    private val keys = FakeBookContentKeys(mutableMapOf(recent to "moby", downloads to "moby"))
    private val prefs = FakeReadingPreferences(log = log).apply {
        saveReadingPosition(recent, ReadingPosition(4, 2, 0))
        saveProgress(recent, 0.4f)
    }
    private val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, "same-book-io") }
    private val io = ioExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() = ioExecutor.shutdown()

    private fun book(uri: String) = Book(title = "T", author = "A", chapters = emptyList(), filePath = uri)

    private fun add(io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) =
        AddBookToLibraryUseCase(library, FakeEntitlementRepository(), sameBookFor(library, keys, prefs, FakeBookFiles(), io))

    @Test
    fun `the prefs are copied before the library row moves and forgotten only after it`() = runTest {
        add().add(book(downloads))

        assertEquals(
            listOf("prefs copy $recent $downloads", "move $recent $downloads", "prefs forget $recent"),
            log.filter { it.startsWith("prefs") || it.startsWith("move") },
        )
        assertEquals(ReadingPosition(4, 2, 0), prefs.getReadingPosition(downloads))
    }

    @Test
    fun `a library move that fails leaves the book and its place under the old uri`() = runTest {
        library.failMove = IOException("disk full")

        val failed = runCatching { add().add(book(downloads)) }.exceptionOrNull()

        assertTrue(failed is IOException)
        assertEquals(setOf(recent, "/b/2", "/b/3"), library.books.value.map { it.uri }.toSet())
        assertEquals("the place stays with the row", ReadingPosition(4, 2, 0), prefs.getReadingPosition(recent))
        assertEquals(0.4f, prefs.getProgress(recent))
        assertEquals("nothing was forgotten", emptyList<String>(), prefs.forgotten)
    }

    @Test
    fun `the prefs' disk writes run on the io dispatcher, not the caller's thread`() = runTest {
        add(io).add(book(downloads))

        assertEquals(listOf("same-book-io", "same-book-io"), prefs.bookThreads.toList())
    }

    @Test
    fun `two adds of one file at once leave one card, not a fourth`() = runTest {
        val other = "content://drive/abc"
        keys.keys[other] = "moby"
        // The first add holds its move until the second has read its file: without serialising, the
        // second still finds the library book under `recent`, skips the free-slot check, moves nothing
        // (the row has gone) and saves a fourth card.
        val secondRead = CompletableDeferred<Unit>()
        keys.beforeRead = { uri -> if (uri == other) secondRead.complete(Unit) }
        var moves = 0
        library.beforeMove = {
            if (moves++ == 0) withContext(Dispatchers.Default) { kotlinx.coroutines.withTimeoutOrNull(500) { secondRead.await() } }
        }
        val useCase = add()

        listOf(async { useCase.add(book(downloads)) }, async { yield(); useCase.add(book(other)) }).awaitAll()

        assertEquals(3, library.books.value.size)
        assertEquals(1, library.books.value.count { it.uri == downloads || it.uri == other })
    }
}
