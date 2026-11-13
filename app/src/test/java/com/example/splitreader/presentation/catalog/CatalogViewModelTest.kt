package com.example.splitreader.presentation.catalog

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.CatalogRepository
import com.example.splitreader.domain.repository.EntitlementRepository
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase.Companion.FREE_BOOK_LIMIT
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.example.splitreader.fakes.FakeBookParser
import com.example.splitreader.fakes.FakeCrashReporter
import com.example.splitreader.presentation.reader.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * CAT-008: re-reading a catalog book the library already owns must open it, never show the
 * "library is full" dialog. [CatalogViewModel.downloadAndOpen] used to gate on
 * `addBookToLibraryUseCase.canAddNew(null)` *before* it knew the book's uri, so the "re-opening an
 * owned book is an upsert, never new" rule could never match — a free user at the cap got the limit
 * dialog even when tapping "Read" on a book they already owned. The fix: ask
 * [CatalogRepository.localUriFor] for the book's deterministic uri *before* gating, and check that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogViewModelTest {

    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    /** Deterministic in-memory stand-in for the real EpubDownloader-backed repository. */
    private class FakeCatalogRepository(private val dir: File) : CatalogRepository {
        val downloadCalls = mutableListOf<CatalogBook>()
        var cancelDownload = false
        override suspend fun search(query: String, source: CatalogSource, page: Int): CatalogPage =
            CatalogPage(emptyList(), false)
        override suspend fun downloadEpub(book: CatalogBook): Uri {
            downloadCalls += book
            if (cancelDownload) throw kotlinx.coroutines.CancellationException("cancelled")
            val file = fileFor(book)
            if (!file.exists()) file.writeBytes(ByteArray(8))
            return Uri.fromFile(file)
        }
        override fun localUriFor(book: CatalogBook): Uri = Uri.fromFile(fileFor(book))
        private fun fileFor(book: CatalogBook): File =
            File(dir, "${book.source.name.lowercase()}_${book.id.replace('/', '_')}.epub")
    }

    private class FakeLibrary(
        var count: Int = 0,
        val existing: MutableSet<String> = mutableSetOf(),
    ) : BookLibraryRepository {
        override suspend fun saveBook(book: Book) {
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
        override suspend fun removeBook(uri: String) = Unit
    }

    private class FakeEntitlement(premium: Boolean) : EntitlementRepository {
        private val state = MutableStateFlow(premium)
        override val isPremium: Flow<Boolean> = state
        override fun setPremium(premium: Boolean) { state.value = premium }
    }

    private fun catalogBook(id: String = "1661") = CatalogBook(
        source = CatalogSource.GUTENBERG,
        id = id,
        title = "The Adventures of Sherlock Holmes",
        author = "Arthur Conan Doyle",
        languages = listOf("en"),
        coverUrl = null,
        epubUrl = "https://www.gutenberg.org/ebooks/$id.epub.images",
    )

    private val crash = FakeCrashReporter()

    private fun viewModel(
        catalogRepository: FakeCatalogRepository,
        library: FakeLibrary,
        premium: Boolean = false,
    ): CatalogViewModel {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return CatalogViewModel(
            catalogRepository = catalogRepository,
            parseBookUseCase = ParseBookUseCase(
                setOf(FakeBookParser(Book(title = "T", author = "A", chapters = emptyList(), filePath = ""))),
                context,
                FakeCrashReporter(),
            ),
            addBookToLibraryUseCase = AddBookToLibraryUseCase(library, FakeEntitlement(premium)),
            crashReporter = crash,
        )
    }

    @Test
    fun `re-reading an owned catalog book at the free-tier limit opens it, no limit dialog`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.cacheDir, "catalog-test-1").apply { mkdirs() }
        val repo = FakeCatalogRepository(dir)
        val book = catalogBook()
        val ownedUri = repo.localUriFor(book).toString()
        val library = FakeLibrary(count = FREE_BOOK_LIMIT, existing = mutableSetOf(ownedUri, "/a.epub", "/b.epub"))
        val vm = viewModel(repo, library)
        val navigated = mutableListOf<String>()
        val collector = backgroundScope.launch { vm.navigationEvent.collect { navigated += it } }

        vm.downloadAndOpen(book)
        awaitReal { navigated.isNotEmpty() || vm.uiState.value.showLimitDialog }

        assertFalse(
            "re-opening an owned catalog book must never hit the free-tier limit dialog",
            vm.uiState.value.showLimitDialog,
        )
        assertEquals(listOf(ownedUri), navigated)
        collector.cancel()
    }

    @Test
    fun `downloading a genuinely new book at the free-tier limit shows the limit dialog and never downloads`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.cacheDir, "catalog-test-2").apply { mkdirs() }
        val repo = FakeCatalogRepository(dir)
        val book = catalogBook(id = "84")
        val library = FakeLibrary(count = FREE_BOOK_LIMIT, existing = mutableSetOf("/a.epub", "/b.epub", "/c.epub"))
        val vm = viewModel(repo, library)

        vm.downloadAndOpen(book)
        awaitReal { vm.uiState.value.showLimitDialog }

        assertTrue(vm.uiState.value.showLimitDialog)
        assertTrue("a blocked download must never touch the network/disk", repo.downloadCalls.isEmpty())
    }

    @Test
    fun `an owned catalog book below the cap opens normally`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.cacheDir, "catalog-test-3").apply { mkdirs() }
        val repo = FakeCatalogRepository(dir)
        val book = catalogBook()
        val ownedUri = repo.localUriFor(book).toString()
        val library = FakeLibrary(count = 1, existing = mutableSetOf(ownedUri))
        val vm = viewModel(repo, library)
        val navigated = mutableListOf<String>()
        val collector = backgroundScope.launch { vm.navigationEvent.collect { navigated += it } }

        vm.downloadAndOpen(book)
        awaitReal { navigated.isNotEmpty() }

        assertFalse(vm.uiState.value.showLimitDialog)
        assertEquals(listOf(ownedUri), navigated)
        collector.cancel()
    }

    @Test
    fun `premium can add a genuinely new catalog book even at the cap`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.cacheDir, "catalog-test-4").apply { mkdirs() }
        val repo = FakeCatalogRepository(dir)
        val book = catalogBook(id = "84")
        val library = FakeLibrary(count = FREE_BOOK_LIMIT, existing = mutableSetOf("/a.epub", "/b.epub", "/c.epub"))
        val vm = viewModel(repo, library, premium = true)
        val navigated = mutableListOf<String>()
        val collector = backgroundScope.launch { vm.navigationEvent.collect { navigated += it } }

        vm.downloadAndOpen(book)
        awaitReal { navigated.isNotEmpty() }

        assertFalse(vm.uiState.value.showLimitDialog)
        assertEquals(1, navigated.size)
        collector.cancel()
    }

    @Test
    fun `a cancelled download is not an error and is not reported`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repo = FakeCatalogRepository(File(context.cacheDir, "catalog-test-5").apply { mkdirs() })
        repo.cancelDownload = true
        val vm = viewModel(repo, FakeLibrary())

        vm.downloadAndOpen(catalogBook())
        awaitReal { repo.downloadCalls.isNotEmpty() }
        runCurrent()

        assertEquals("cancellation must not show an error", null, vm.uiState.value.errorMessage)
        assertTrue("cancellation must not be reported as a crash: ${crash.recorded}", crash.recorded.isEmpty())
    }

    // runCurrent, not advanceUntilIdle: ParseBookUseCase/download run on the real Dispatchers.IO,
    // which virtual time cannot drive (same reasoning as HomeImportLoadingTest).
    private fun TestScope.awaitReal(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            runCurrent()
            if (condition()) return
            Thread.sleep(10)
        }
        error("Timed out waiting for condition")
    }
}
