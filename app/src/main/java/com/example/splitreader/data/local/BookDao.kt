package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * A row with `removedAt` set is a deleted book kept only for its bookmarks and notes (see
 * [BookEntity.removedAt]). Every read here answers for the library, so every read excludes it;
 * a new query on this table must do the same -- except the Same book section at the end, which
 * exists to find removed books.
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
        // The content key is the Same book's, not the parse's: a save never forgets it.
        val key = book.contentKey ?: anyRow(book.uri)?.contentKey
        upsert(book.copy(coverPath = cover, contentKey = key))
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

    // ── The Same book: these see removed rows too ────────────────────────────────────────────

    @Query("SELECT uri, contentKey, removedAt IS NOT NULL AS removed FROM books WHERE uri = :uri")
    suspend fun storedRow(uri: String): StoredBookRow?

    // A book in the library first: a file added twice before v29 has two rows with one key.
    @Query(
        "SELECT uri, contentKey, removedAt IS NOT NULL AS removed FROM books WHERE contentKey = :contentKey " +
            "ORDER BY removedAt IS NOT NULL, lastOpenedAt DESC LIMIT 1"
    )
    suspend fun rowWithContent(contentKey: String): StoredBookRow?

    @Query("UPDATE books SET contentKey = :contentKey WHERE uri = :uri")
    suspend fun setContentKey(uri: String, contentKey: String)

    @Query("SELECT * FROM books WHERE uri = :uri")
    suspend fun anyRow(uri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNew(book: BookEntity)

    @Query("UPDATE bookmarks SET bookUri = :to WHERE bookUri = :from")
    suspend fun repointBookmarks(from: String, to: String)

    @Query("UPDATE notes SET bookUri = :to WHERE bookUri = :from")
    suspend fun repointNotes(from: String, to: String)

    @Query("UPDATE saved_words SET bookUri = :to WHERE bookUri = :from")
    suspend fun repointSavedWords(from: String, to: String)

    @Query("UPDATE reading_sessions SET bookUri = :to WHERE bookUri = :from")
    suspend fun repointReadingSessions(from: String, to: String)

    /**
     * Moves the book at [from] to [to] (which must have no row) with everything that references it.
     * The foreign keys have no ON UPDATE action and are checked per statement, so the uri cannot be
     * updated in place: the new row goes in first, the children are repointed to it, and only then
     * is the old row -- by now without children -- deleted. One transaction: all of it or nothing.
     * The new row has no cover: the old one's file is named after [from] and goes with its files.
     */
    @Transaction
    suspend fun move(from: String, to: String) {
        val row = anyRow(from) ?: return
        insertNew(row.copy(uri = to, coverPath = null))
        repointBookmarks(from, to)
        repointNotes(from, to)
        repointSavedWords(from, to)
        repointReadingSessions(from, to)
        deleteByUri(from)
    }
}

/** A books row as the Same book lookup reads it. */
data class StoredBookRow(val uri: String, val contentKey: String?, val removed: Boolean)
