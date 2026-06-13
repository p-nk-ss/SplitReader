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
 *  - re-opening a book already in the library never counts as "new" (it's an upsert), and
 *  - a reinstall wipes books to zero and the count resets with them — there's no lifetime tally to game.
 *
 * Premium users (see [EntitlementRepository]) bypass the limit entirely.
 */
class AddBookToLibraryUseCase @Inject constructor(
    private val library: BookLibraryRepository,
    private val entitlement: EntitlementRepository,
) {
    /** Saves [book] if allowed, otherwise returns [AddResult.LimitReached] without saving. */
    suspend fun add(book: Book): AddResult =
        if (canAddNew(book.filePath)) {
            library.saveBook(book)
            AddResult.Added
        } else {
            AddResult.LimitReached
        }

    /**
     * Cheap pre-check to avoid an expensive download/parse when already at the limit. [uri] is the
     * target book's identity if known (so re-downloads of an owned book are allowed); pass `null`
     * when it isn't yet known — [add] re-checks authoritatively before saving either way.
     */
    suspend fun canAddNew(uri: String?): Boolean {
        if (entitlement.isPremium.first()) return true
        if (uri != null && library.exists(uri)) return true
        return library.bookCount() < FREE_BOOK_LIMIT
    }

    companion object {
        const val FREE_BOOK_LIMIT = 3
    }
}
