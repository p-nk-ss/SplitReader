package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.domain.repository.SavedWordRepository
import javax.inject.Inject

enum class SaveWordResult { SAVED, DUPLICATE, EMPTY }

/** Strips leading/trailing punctuation and lowercases, keeping inner apostrophes/hyphens. */
fun normalizeWord(raw: String): String =
    raw.trim().trim { !it.isLetterOrDigit() }.lowercase()

/**
 * Saves a word to the vocabulary: normalizes it (see [normalizeWord]), skips duplicates for the
 * same source language, and persists it with [translation] — the text the selection popup already
 * shows, so saving never sends a second translation request. Returns a [SaveWordResult].
 */
class SaveWordUseCase @Inject constructor(
    private val savedWordRepository: SavedWordRepository,
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
        translation: String,
    ): SaveWordResult {
        val normalized = normalizeWord(word)
        if (normalized.isEmpty()) return SaveWordResult.EMPTY

        // Avoid duplicates: one entry per (word, source language)
        if (savedWordRepository.findByWordAndLang(normalized, sourceLang.code) != null) {
            return SaveWordResult.DUPLICATE
        }

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
