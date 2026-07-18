package com.example.splitreader.domain.model

/** A finished reading session. [id] is 0 until persisted (Room autoGenerates on insert). */
data class ReadingSession(
    val bookUri: String?,
    val bookTitle: String,
    val sourceLang: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val paragraphsRead: Int,
    val id: Long = 0,
)
