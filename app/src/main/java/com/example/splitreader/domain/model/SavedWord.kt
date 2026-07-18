package com.example.splitreader.domain.model

/** A saved vocabulary word. [id] is 0 until persisted (Room autoGenerates on insert). */
data class SavedWord(
    val word: String,
    val sourceLang: String,
    val targetLang: String,
    val translation: String,
    val bookUri: String?,
    val bookTitle: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val contextSnippet: String,
    val partOfSpeech: String? = null,
    val note: String? = null,
    val id: Long = 0,
    val savedAt: Long = 0,
)
