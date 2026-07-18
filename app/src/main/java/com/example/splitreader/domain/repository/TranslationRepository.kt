package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language

/** Translates text between languages, caching results to avoid repeat work. */
interface TranslationRepository {
    suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String

    /** Number of cached translations (for the Settings storage display). */
    suspend fun cachedCount(): Int

    /** Clears all cached translations. */
    suspend fun clearCache()
}
