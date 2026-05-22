package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uri"], childColumns = ["bookUri"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("startedAt"), Index("bookUri")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String?,
    val bookTitle: String,
    val sourceLang: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val paragraphsRead: Int,
)
