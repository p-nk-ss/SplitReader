package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.model.SavedWord

fun SavedWordEntity.toDomain(): SavedWord = SavedWord(
    word = word, sourceLang = sourceLang, targetLang = targetLang, translation = translation,
    bookUri = bookUri, bookTitle = bookTitle, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, contextSnippet = contextSnippet,
    partOfSpeech = partOfSpeech, note = note, id = id, savedAt = savedAt,
)

fun SavedWord.toEntity(): SavedWordEntity = SavedWordEntity(
    id = id, word = word, partOfSpeech = partOfSpeech, sourceLang = sourceLang,
    targetLang = targetLang, translation = translation, bookUri = bookUri, bookTitle = bookTitle,
    chapterIndex = chapterIndex, paragraphIndex = paragraphIndex, contextSnippet = contextSnippet,
    note = note, savedAt = savedAt,
)
