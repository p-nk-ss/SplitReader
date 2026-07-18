package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.EntitlementRepository
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase.Companion.FREE_BOOK_LIMIT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the free-tier chokepoint: this is the single place that decides whether a book may be
 * added, and the whole premium upsell rests on it. The rules under test:
 *  - premium bypasses the limit entirely,
 *  - re-opening a book already in the library is never "new" (upsert), even at the limit,
 *  - otherwise the library must be strictly below [FREE_BOOK_LIMIT].
 */
class AddBookToLibraryUseCaseTest {

    /** Records saves and lets each test preset the library size + which URIs already exist. */
    private class FakeLibrary(
        var count: Int = 0,
        val existing: MutableSet<String> = mutableSetOf(),
    ) : BookLibraryRepository {
        val saved = mutableListOf<Book>()
        override suspend fun saveBook(book: Book) {
            saved += book
            if (book.filePath !in existing) {
                existing += book.filePath
                count++
            }
        }
        override suspend fun bookCount(): Int = count
        override suspend fun exists(uri: String): Boolean = uri in existing
        override fun getAllBooks(): Flow<List<LibraryBook>> = flowOf(emptyList())
        override suspend fun touchBook(uri: String) = Unit
        override suspend fun deleteBook(uri: String) = Unit
    }

    private class FakeEntitlement(premium: Boolean) : EntitlementRepository {
        private val state = MutableStateFlow(premium)
        override val isPremium: Flow<Boolean> = state
        override fun setPremium(premium: Boolean) { state.value = premium }
    }

    private fun book(path: String) =
        Book(title = "T", author = "A", chapters = emptyList(), filePath = path)

    private fun useCase(library: FakeLibrary, premium: Boolean) =
        AddBookToLibraryUseCase(library, FakeEntitlement(premium))

    // ── canAddNew ─────────────────────────────────────────────────────────────

    @Test
    fun `free user under the limit can add a new book`() = runTest {
        val uc = useCase(FakeLibrary(count = FREE_BOOK_LIMIT - 1), premium = false)
        assertTrue(uc.canAddNew("/new.epub"))
    }

    @Test
    fun `free user at the limit cannot add a new book`() = runTest {
        val uc = useCase(FakeLibrary(count = FREE_BOOK_LIMIT), premium = false)
        assertFalse(uc.canAddNew("/new.epub"))
    }

    @Test
    fun `free user at the limit can still re-open a book already in the library`() = runTest {
        val library = FakeLibrary(count = FREE_BOOK_LIMIT, existing = mutableSetOf("/owned.epub"))
        val uc = useCase(library, premium = false)
        assertTrue(uc.canAddNew("/owned.epub"))
    }

    @Test
    fun `premium user at the limit can add a new book`() = runTest {
        val uc = useCase(FakeLibrary(count = FREE_BOOK_LIMIT + 5), premium = true)
        assertTrue(uc.canAddNew("/new.epub"))
    }

    @Test
    fun `free user at the limit with unknown uri cannot add`() = runTest {
        // uri == null means identity isn't known yet (pre-download); must fall back to the count check.
        val uc = useCase(FakeLibrary(count = FREE_BOOK_LIMIT), premium = false)
        assertFalse(uc.canAddNew(null))
    }

    @Test
    fun `free user under the limit with unknown uri can add`() = runTest {
        val uc = useCase(FakeLibrary(count = FREE_BOOK_LIMIT - 1), premium = false)
        assertTrue(uc.canAddNew(null))
    }

    // ── add ───────────────────────────────────────────────────────────────────

    @Test
    fun `add saves and returns Added when under the limit`() = runTest {
        val library = FakeLibrary(count = 0)
        val result = useCase(library, premium = false).add(book("/a.epub"))
        assertEquals(AddResult.Added, result)
        assertEquals(listOf("/a.epub"), library.saved.map { it.filePath })
    }

    @Test
    fun `add returns LimitReached and does not save when free user is at the limit`() = runTest {
        val library = FakeLibrary(count = FREE_BOOK_LIMIT)
        val result = useCase(library, premium = false).add(book("/blocked.epub"))
        assertEquals(AddResult.LimitReached, result)
        assertTrue("must not persist when blocked", library.saved.isEmpty())
    }

    @Test
    fun `add re-opens an owned book at the limit without counting as new`() = runTest {
        val library = FakeLibrary(count = FREE_BOOK_LIMIT, existing = mutableSetOf("/owned.epub"))
        val result = useCase(library, premium = false).add(book("/owned.epub"))
        assertEquals(AddResult.Added, result)
        assertEquals(FREE_BOOK_LIMIT, library.count) // upsert: count unchanged
    }

    @Test
    fun `add lets a premium user exceed the limit`() = runTest {
        val library = FakeLibrary(count = FREE_BOOK_LIMIT)
        val result = useCase(library, premium = true).add(book("/extra.epub"))
        assertEquals(AddResult.Added, result)
        assertEquals(FREE_BOOK_LIMIT + 1, library.count)
    }
}
