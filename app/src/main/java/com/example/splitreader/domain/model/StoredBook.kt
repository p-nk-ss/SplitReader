package com.example.splitreader.domain.model

/**
 * A book the library holds a row for -- in the library, or a Removed book -- as the Same book
 * lookup sees it. [contentKey] is null for a book not opened since v29 (or whose file could not be read).
 */
data class StoredBook(
    val uri: String,
    val contentKey: String?,
    val removed: Boolean,
)
