package com.example.splitreader.presentation.catalog

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.auth.DriveAuthClient
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.DrivePickedFile
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.repository.AuthRepository
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.DriveRepository
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

/** Same rule as CAT-008, for Drive: re-reading an owned Drive book at the free cap opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveViewModelTest {

    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeDriveRepository(private val dir: File) : DriveRepository {
        val downloads = mutableListOf<DrivePickedFile>()
        private fun target(f: DrivePickedFile) = File(dir, "${f.fileId}.epub")
        override suspend fun downloadFile(file: DrivePickedFile, accessToken: String): Uri {
            downloads += file
            val t = target(file)
            if (!t.exists()) t.writeBytes(ByteArray(8))
            return Uri.fromFile(t)
        }
        // Like the real repository: only a file that is already on disk is "local".
        override suspend fun localUriFor(file: DrivePickedFile): Uri? =
            target(file).takeIf { it.exists() }?.let { Uri.fromFile(it) }
        fun preexisting(f: DrivePickedFile) = target(f).writeBytes(ByteArray(8))
    }

    private class FakeLibrary(var count: Int, val existing: MutableSet<String>) : BookLibraryRepository {
        override suspend fun saveBook(book: Book) { if (existing.add(book.filePath)) count++ }
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

    private object SignedOutAuth : AuthRepository {
        override val authState: Flow<AuthState> = flowOf(AuthState.SignedOut)
        override val currentUser: AuthUser? = null
        private fun no(): AuthResult = error("not used")
        override suspend fun registerWithEmail(email: String, password: String, displayName: String?) = no()
        override suspend fun signInWithEmail(email: String, password: String) = no()
        override suspend fun signInWithGoogle(activityContext: Context) = no()
        override suspend fun sendEmailVerification() = no()
        override suspend fun reloadUser() = no()
        override suspend fun sendPasswordReset(email: String) = no()
        override fun signOut() = Unit
        override suspend fun deleteAccount() = no()
        override suspend fun reauthenticateWithPassword(password: String) = no()
        override suspend fun reauthenticateWithGoogle(activityContext: Context) = no()
        override suspend fun signInMethodsForEmail(email: String) = emptyList<String>()
    }

    private val picked = DrivePickedFile(fileId = "abc123", name = "b.epub")
    private lateinit var repo: FakeDriveRepository
    private val parser = FakeBookParser(Book(title = "T", author = "A", chapters = emptyList(), filePath = ""))

    private fun viewModel(library: FakeLibrary, premium: Boolean = false, realRepo: DriveRepository? = null): DriveViewModel {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.cacheDir, "drive-test-${System.nanoTime()}").apply { mkdirs() }
        repo = FakeDriveRepository(dir)
        return DriveViewModel(
            context = context,
            authRepository = SignedOutAuth,
            driveAuthClient = DriveAuthClient(),
            driveRepository = realRepo ?: repo,
            parseBookUseCase = ParseBookUseCase(
                setOf(parser),
                context,
                FakeCrashReporter(),
            ),
            addBookToLibraryUseCase = AddBookToLibraryUseCase(library, FakeEntitlement(premium)),
        )
    }

    private fun TestScope.run(vm: DriveViewModel, navigated: MutableList<String>) {
        val collector = backgroundScope.launch { vm.navigationEvent.collect { navigated += it } }
        backgroundScope.launch { vm.downloadAndOpen(picked, "token") }
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            runCurrent()
            if (navigated.isNotEmpty() || vm.uiState.value.showLimitDialog) break
            Thread.sleep(10)
        }
        collector.cancel()
    }

    @Test
    fun `re-reading an owned drive book at the free-tier limit opens it, no limit dialog`() = runTest(main.dispatcher) {
        val library = FakeLibrary(FREE_BOOK_LIMIT, mutableSetOf("/a", "/b"))
        val vm = viewModel(library)
        repo.preexisting(picked)
        library.existing += repo.localUriFor(picked).toString()
        val navigated = mutableListOf<String>()
        run(vm, navigated)
        assertFalse("owned Drive book must never hit the free-tier dialog", vm.uiState.value.showLimitDialog)
        assertEquals(1, navigated.size)
    }

    @Test
    fun `a new drive book at the free-tier limit shows the dialog and never downloads`() = runTest(main.dispatcher) {
        val vm = viewModel(FakeLibrary(FREE_BOOK_LIMIT, mutableSetOf("/a", "/b", "/c")))
        run(vm, mutableListOf())
        assertTrue(vm.uiState.value.showLimitDialog)
        assertTrue(repo.downloads.isEmpty())
    }

    @Test
    fun `an owned drive book below the cap opens`() = runTest(main.dispatcher) {
        val library = FakeLibrary(1, mutableSetOf())
        val vm = viewModel(library)
        repo.preexisting(picked)
        library.existing += repo.localUriFor(picked).toString()
        val navigated = mutableListOf<String>()
        run(vm, navigated)
        assertFalse(vm.uiState.value.showLimitDialog)
        assertEquals(1, navigated.size)
    }

    @Test
    fun `premium adds a new drive book at the cap`() = runTest(main.dispatcher) {
        val vm = viewModel(FakeLibrary(FREE_BOOK_LIMIT, mutableSetOf("/a", "/b", "/c")), premium = true)
        val navigated = mutableListOf<String>()
        run(vm, navigated)
        assertFalse(vm.uiState.value.showLimitDialog)
        assertEquals(1, navigated.size)
    }

    /**
     * The file is on disk (a download that was never added, or a book deleted with its row while the
     * file survived) but the library has NO row for it: it is a new book and the cap applies. Uses
     * the real repository, so "it is on disk" is the real folder lookup.
     */
    @Test
    fun `a drive file on disk with no library row at the cap shows the limit dialog`() = runTest(main.dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val onDisk = File(File(context.filesDir, "drive").apply { mkdirs() }, "${picked.fileId}.epub").apply { writeBytes(ByteArray(8)) }
        val real = com.example.splitreader.data.repository.DriveRepositoryImpl(okhttp3.OkHttpClient(), context)
        // (Not asked here: the lookup hops to Dispatchers.IO, and resuming this unconfined test body
        // there would queue the view model's coroutine behind it. DriveRepositoryImplTest covers it.)
        val vm = viewModel(FakeLibrary(FREE_BOOK_LIMIT, mutableSetOf("/a", "/b", "/c")), realRepo = real)
        val navigated = mutableListOf<String>()

        run(vm, navigated)

        assertTrue("a file on disk is not an owned book", vm.uiState.value.showLimitDialog)
        assertEquals(emptyList<String>(), navigated)
        assertEquals("stopped at the gate, before the file on disk is parsed", 0, parser.parses.get())
        onDisk.delete()
    }
}
