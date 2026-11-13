package com.example.splitreader.domain.usecase

import com.example.splitreader.fakes.FakeBookFiles
import com.example.splitreader.fakes.FakeBookLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SweepOrphanBookFilesUseCaseTest {

    private val library = FakeBookLibraryRepository().apply { put("content://books/x"); put("content://books/y") }
    private val files = FakeBookFiles()
    private val sweep = SweepOrphanBookFilesUseCase(library, files)

    @Test
    fun `the sweep keeps the books the library shows`() = runTest {
        sweep()
        assertEquals(listOf(listOf("content://books/x", "content://books/y")), files.sweeps)
    }

    /** Its files were deleted when it was removed; whatever is there now was written by a parse that outlived it. */
    @Test
    fun `a removed book is not kept -- only its reading data is`() = runTest {
        library.removeBook("content://books/x")
        sweep()
        assertEquals(listOf(listOf("content://books/y")), files.sweeps)
    }

    @Test
    fun `a sweep that fails is not an error -- it runs again on the next start`() = runTest {
        files.failSweep = IllegalStateException("disk")
        sweep()
    }

    /** No list, no sweep: with an empty list every book's files would count as orphans. */
    @Test
    fun `a library that can not be read sweeps nothing`() = runTest {
        val unreadable = object : com.example.splitreader.domain.repository.BookLibraryRepository by library {
            override fun getAllBooks() = kotlinx.coroutines.flow.flow<List<com.example.splitreader.domain.model.LibraryBook>> { error("db") }
        }
        SweepOrphanBookFilesUseCase(unreadable, files)()
        assertEquals(emptyList<List<String>>(), files.sweeps)
    }
}
