package com.example.splitreader.domain.model

/** A paragraph-level bookmark within a book. */
data class Bookmark(
    val id: Long,
    val bookUri: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val label: String?,
    val createdAt: Long,
)
