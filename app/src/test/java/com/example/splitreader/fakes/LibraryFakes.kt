package com.example.splitreader.fakes

import android.content.Context
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.model.StoredBook
import com.example.splitreader.domain.repository.AuthRepository
import com.example.splitreader.domain.repository.BookContentKeys
import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.usecase.SameBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * An in-memory library. A removed book is hidden from every read until it is saved again, as in the
 * real one. When [log] is given, a delete appends `"row <uri>"` and a remove `"hide <uri>"` to it.
 */
internal class FakeBookLibraryRepository(
    private val log: MutableList<String>? = null,
    var failDelete: Throwable? = null,
    /** Thrown by [moveBook] before it changes anything (a failed transaction). */
    var failMove: Throwable? = null,
) : BookLibraryRepository {
    val books = MutableStateFlow<List<LibraryBook>>(emptyList())

    /** Books taken out with [removeBook]: kept, hidden. */
    val removed = mutableSetOf<String>()

    fun put(uri: String) = books.update { list ->
        removed -= uri
        list.filterNot { it.uri == uri } + LibraryBook(
            uri = uri, title = "T", author = "A", coverPath = null, lastOpenedAt = 0L, chapterCount = 0, synopsis = null,
        )
    }

    override fun getAllBooks(): Flow<List<LibraryBook>> = books
    override suspend fun saveBook(book: Book) { put(book.filePath) }
    override suspend fun touchBook(uri: String) = Unit
    override suspend fun removeBook(uri: String) {
        failDelete?.let { throw it }
        log?.add("hide $uri")
        if (books.value.any { it.uri == uri }) removed += uri
        books.update { list -> list.filterNot { it.uri == uri } }
    }
    override suspend fun deleteBook(uri: String) {
        failDelete?.let { throw it }
        log?.add("row $uri")
        removed -= uri
        books.update { list -> list.filterNot { it.uri == uri } }
    }
    override suspend fun bookCount(): Int = books.value.size
    override suspend fun exists(uri: String): Boolean = books.value.any { it.uri == uri }

    val contentKeys = mutableMapOf<String, String>()
    override suspend fun stored(uri: String): StoredBook? = when {
        exists(uri) -> StoredBook(uri, contentKeys[uri], removed = false)
        uri in removed -> StoredBook(uri, contentKeys[uri], removed = true)
        else -> null
    }
    override suspend fun withContent(contentKey: String): StoredBook? =
        contentKeys.filterValues { it == contentKey }.keys.mapNotNull { stored(it) }.minByOrNull { it.removed }
    override suspend fun setContentKey(uri: String, contentKey: String) { contentKeys[uri] = contentKey }
    /** Runs at the start of every [moveBook]: a test can hold a move here. */
    var beforeMove: suspend () -> Unit = {}
    override suspend fun moveBook(from: String, to: String) {
        beforeMove()
        failMove?.let { throw it }
        log?.add("move $from $to")
        val wasRemoved = from in removed
        books.update { list -> list.map { if (it.uri == from) it.copy(uri = to, coverPath = null) else it } }
        if (wasRemoved) { removed -= from; removed += to }
        contentKeys.remove(from)?.let { contentKeys[to] = it }
    }
}

/**
 * Library rows by uri only -- no content keys, nothing to move: for the tests the Same book is not
 * about. A book is "stored" while [exists] says so.
 */
internal interface UriOnlyLibrary : BookLibraryRepository {
    override suspend fun stored(uri: String): StoredBook? =
        if (exists(uri)) StoredBook(uri, contentKey = null, removed = false) else null
    override suspend fun withContent(contentKey: String): StoredBook? = null
    override suspend fun setContentKey(uri: String, contentKey: String) = Unit
    override suspend fun moveBook(from: String, to: String) = error("no Same book in this test")
}

/** Content keys by uri; a uri not in [keys] is a file that cannot be read (null). Counts the reads. */
internal class FakeBookContentKeys(val keys: MutableMap<String, String> = mutableMapOf()) : BookContentKeys {
    val reads = java.util.Collections.synchronizedList(mutableListOf<String>())
    /** Runs before each read: a test can hold reads here (e.g. `CompletableDeferred.await()`). */
    var beforeRead: suspend (String) -> Unit = {}
    override suspend fun keyOf(uri: String): String? {
        reads += uri
        beforeRead(uri)
        return keys[uri]
    }
}

/** A [SameBook] over [library] that never recognises another uri: every file is unreadable. */
internal fun sameBookFor(
    library: BookLibraryRepository,
    keys: BookContentKeys = FakeBookContentKeys(),
    preferences: ReadingPreferences = FakeReadingPreferences(),
    files: BookFiles = FakeBookFiles(),
    io: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
) = SameBook(library, keys, preferences, files, io)

/** Records the books whose files were deleted. When [log] is given, appends `"files <uri>"` to it. */
internal class FakeBookFiles(
    private val log: MutableList<String>? = null,
    var fail: Throwable? = null,
) : BookFiles {
    val deleted = mutableListOf<String>()
    override suspend fun deleteFor(bookUri: String) {
        fail?.let { throw it }
        log?.add("files $bookUri")
        deleted += bookUri
    }

    /** The library uris each orphan sweep was given. */
    val sweeps = mutableListOf<List<String>>()
    var failSweep: Throwable? = null
    override suspend fun sweepOrphans(libraryUris: Collection<String>) {
        failSweep?.let { throw it }
        sweeps += libraryUris.toList()
    }
}

/** Nobody is signed in; every sign-in call is an error. */
internal object SignedOutAuth : AuthRepository {
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
    override suspend fun signInMethodsForEmail(email: String): List<String> = emptyList()
}
