package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val lastOpenedAt: Long,
    val chapterCount: Int,
    val synopsis: String? = null,
    /**
     * When the reader deleted this book but kept their reading data; null for a book in the library.
     * A removed row exists only to hold the book's bookmarks and notes (they cascade with the row)
     * until the same book is added again: no library query may return or count it.
     */
    val removedAt: Long? = null,
    /**
     * SHA-256 (hex) of the book file's content: the same file under another uri is the same book.
     * Null for a book not opened since v29 -- and for one whose file could not be read then.
     */
    val contentKey: String? = null,
)
