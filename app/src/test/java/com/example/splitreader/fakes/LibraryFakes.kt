package com.example.splitreader.fakes

import android.content.Context
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.LibraryBook
import com.example.splitreader.domain.repository.AuthRepository
import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
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
}

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
