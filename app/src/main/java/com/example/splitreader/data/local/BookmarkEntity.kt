package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uri"], childColumns = ["bookUri"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("bookUri")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val label: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
