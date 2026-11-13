package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * A row with `removedAt` set is a deleted book kept only for its bookmarks and notes (see
 * [BookEntity.removedAt]). Every read here answers for the library, so every read excludes it;
 * a new query on this table must do the same.
 */
@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE removedAt IS NULL ORDER BY lastOpenedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    // Must update the existing row in place. Bookmarks/notes (CASCADE) and saved words/reading
    // sessions (SET NULL) reference books.uri, and INSERT OR REPLACE deletes the row first, which
    // fires those actions and wipes or orphans the reader's data on every re-import.
    // Upserting a removed book (removedAt = null in the new row) is what brings it back.
    @Upsert
    suspend fun upsert(book: BookEntity)

    /**
     * Saves [book]; when it comes without a cover (a re-parse whose picture write failed) the cover
     * stored for the book in the library is kept -- while its file is there: a path to a file that
     * is gone would be a card without its cover until a parse writes one again. The read and the write are ONE transaction: as two
     * statements, a removal landing between them (it deletes the cover file) was undone by the write,
     * which brought the book back with the deleted file's path.
     */
    @Transaction
    suspend fun saveKeepingCover(book: BookEntity) {
        val cover = book.coverPath ?: coverPathOf(book.uri)?.takeIf { stored ->
            try { File(stored).isFile } catch (_: Exception) { false }
        }
        upsert(book.copy(coverPath = cover))
    }

    // Not filtered: a touch changes nothing a library read can see on a removed row.
    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE uri = :uri")
    suspend fun updateLastOpenedAt(uri: String, timestamp: Long)

    /** Hides the book and keeps its row (and with it the book's bookmarks and notes). */
    @Query("UPDATE books SET removedAt = :timestamp WHERE uri = :uri AND removedAt IS NULL")
    suspend fun markRemoved(uri: String, timestamp: Long)

    /** Deletes the row -- removed or not -- and with it the book's bookmarks and notes. */
    @Query("DELETE FROM books WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    // A removed book's cover file was deleted with its other files: its stored path is dead, and
    // the save that brings the book back must not inherit it.
    @Query("SELECT coverPath FROM books WHERE uri = :uri AND removedAt IS NULL")
    suspend fun coverPathOf(uri: String): String?

    @Query("SELECT COUNT(*) FROM books WHERE removedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uri = :uri AND removedAt IS NULL)")
    suspend fun exists(uri: String): Boolean
}
