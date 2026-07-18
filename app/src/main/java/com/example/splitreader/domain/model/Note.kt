package com.example.splitreader.domain.model

/** A paragraph-anchored note or highlight within a book. */
data class Note(
    val bookUri: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val body: String,
    val isHighlight: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
