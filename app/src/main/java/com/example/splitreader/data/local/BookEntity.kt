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
)
