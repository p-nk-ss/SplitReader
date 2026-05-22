package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "notes",
    primaryKeys = ["bookUri", "chapterIndex", "paragraphIndex"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uri"], childColumns = ["bookUri"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("bookUri")],
)
data class NoteEntity(
    val bookUri: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val body: String,
    val isHighlight: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
