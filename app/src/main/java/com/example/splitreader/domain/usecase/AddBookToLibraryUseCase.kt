package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Outcome of trying to add a book to the library. */
sealed interface AddResult {
    data object Added : AddResult
    data object LimitReached : AddResult
}

/**
 * The single chokepoint for adding a book to the library, enforcing the free-tier limit.
 *
 * The limit is on the *current* number of books in the library (per device, not per account), so:
 *  - re-opening a book already in the library never counts as "new" (it's an upsert) -- nor does
 *    the Same book picked under another uri: it moves to that uri (see [SameBook]),
 *  - a deleted book is not in the library, even when its bookmarks and notes were kept: it frees its
 *    slot, and adding it again -- under its uri or as the Same book under another -- is "new" and
 *    needs one (see [DeleteBookUseCase]), and
 *  - a reinstall wipes books to zero and the count resets with them — there's no lifetime tally to game.
 *
 * Premium users (see [EntitlementRepository]) bypass the limit entirely.
 */
class AddBookToLibraryUseCase @Inject constructor(
    private val library: BookLibraryRepository,
    private val entitlement: EntitlementRepository,
    private val sameBook: SameBook,
) {
    /** Saves [book] if allowed, otherwise returns [AddResult.LimitReached] without saving. */
    suspend fun add(book: Book): AddResult {
        val found = sameBook.find(book.filePath)
        if (!found.inLibrary && !entitlement.isPremium.first() && library.bookCount() >= FREE_BOOK_LIMIT) {
            return AddResult.LimitReached
        }
        sameBook.save(book, found)
        return AddResult.Added
    }

    /**
     * Cheap pre-check to avoid an expensive download/parse when already at the limit. [uri] is the
     * target book's identity if known (so re-downloads of an owned book are allowed); pass `null`
     * when it isn't yet known — [add] re-checks authoritatively before saving either way.
     * It goes by uri only (no file read): at the limit, the Same book under a new uri is refused
     * here although [add] would take it.
     */
    suspend fun canAddNew(uri: String?): Boolean {
        if (entitlement.isPremium.first()) return true
        if (uri != null && library.exists(uri)) return true
        return library.bookCount() < FREE_BOOK_LIMIT
    }

    /**
     * Call when a library book is opened: a book added before v29 gets its content key, so it is
     * recognised as the Same book when it is next added from elsewhere. Reads the file only then;
     * never fails (an unreadable file just stays without a key).
     */
    suspend fun rememberContent(uri: String) = sameBook.rememberContent(uri)

    companion object {
        const val FREE_BOOK_LIMIT = 3
    }
}
