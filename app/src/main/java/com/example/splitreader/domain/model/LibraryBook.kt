package com.example.splitreader.domain.model

/** A book in the user's library (a persisted library row), distinct from the parsed [Book]. */
data class LibraryBook(
    val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val lastOpenedAt: Long,
    val chapterCount: Int,
    val synopsis: String?,
)
