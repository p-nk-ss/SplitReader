package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_words",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uri"], childColumns = ["bookUri"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("bookUri"), Index("sourceLang")],
)
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val partOfSpeech: String? = null,
    val sourceLang: String,
    val targetLang: String,
    val translation: String,
    val bookUri: String?,
    val bookTitle: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val contextSnippet: String,
    val note: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)
