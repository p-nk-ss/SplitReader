package com.example.splitreader.domain.usecase

import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.repository.SavedWordRepository
import javax.inject.Inject

class SaveWordUseCase @Inject constructor(
    private val savedWordRepository: SavedWordRepository,
    private val translateTextUseCase: TranslateTextUseCase,
) {
    suspend operator fun invoke(
        word: String,
        contextSnippet: String,
        sourceLang: Language,
        targetLang: Language,
        bookUri: String?,
        bookTitle: String,
        chapterIndex: Int,
        paragraphIndex: Int,
    ) {
        var translation = ""
        translateTextUseCase(
            paragraphs = listOf(word),
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            startIndex = 0,
            endIndex = 0,
        ).collect { state ->
            if (state is com.example.splitreader.domain.model.TranslationState.Partial) {
                translation = state.text
            }
        }
        savedWordRepository.save(
            SavedWordEntity(
                word = word,
                sourceLang = sourceLang.code,
                targetLang = targetLang.code,
                translation = translation,
                bookUri = bookUri,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                contextSnippet = contextSnippet.take(120),
            )
        )
    }
}
