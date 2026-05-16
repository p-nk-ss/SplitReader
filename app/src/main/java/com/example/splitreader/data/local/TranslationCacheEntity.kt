package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_cache")
data class TranslationCacheEntity(
    @PrimaryKey val id: String,
    val originalText: String,
    val translatedText: String,
    val targetLanguage: String,
    val timestamp: Long = System.currentTimeMillis()
)
