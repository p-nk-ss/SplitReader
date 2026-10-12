package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.SavedWordRepository
import javax.inject.Inject

enum class SaveWordResult { SAVED, DUPLICATE, EMPTY, TRANSLATION_FAILED }

/** Strips leading/trailing punctuation and lowercases, keeping inner apostrophes/hyphens. */
fun normalizeWord(raw: String): String =
    raw.trim().trim { !it.isLetterOrDigit() }.lowercase()

/**
 * Saves a word to the vocabulary: normalizes it (see [normalizeWord]), skips duplicates for the
 * same source language, translates it, and persists the entry. A word whose translation failed is
 * not saved ([SaveWordResult.TRANSLATION_FAILED]). Returns a [SaveWordResult].
 */
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
        provider: TranslationProvider,
    ): SaveWordResult {
        val normalized = normalizeWord(word)
        if (normalized.isEmpty()) return SaveWordResult.EMPTY

        // Avoid duplicates: one entry per (word, source language)
        if (savedWordRepository.findByWordAndLang(normalized, sourceLang.code) != null) {
            return SaveWordResult.DUPLICATE
        }

        var translation = ""
        var failed = false
        translateTextUseCase(
            paragraphs = listOf(normalized),
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            provider = provider,
            startIndex = 0,
            endIndex = 0,
        ).collect { state ->
            when (state) {
                is TranslationState.Partial -> translation = state.text
                is TranslationState.Error -> failed = true
                else -> Unit
            }
        }
        // An untranslated entry would be permanent: the duplicate guard above blocks re-saving it.
        if (failed && translation.isEmpty()) return SaveWordResult.TRANSLATION_FAILED
        savedWordRepository.save(
            SavedWord(
                word = normalized,
                sourceLang = sourceLang.code,
                targetLang = targetLang.code,
                translation = translation,
                bookUri = bookUri,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                contextSnippet = contextSnippet.take(120),
                savedAt = System.currentTimeMillis(),
            )
        )
        return SaveWordResult.SAVED
    }
}
