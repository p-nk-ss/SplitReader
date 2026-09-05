package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
import kotlinx.coroutines.flow.Flow

/** Translates text between languages, caching results to avoid repeat work. */
interface TranslationRepository {
    suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String

    /**
     * Pre-flight for the resolved provider (offline packs download here). Emits progress 0f..1f and
     * completes when the provider is ready; empty for providers with nothing to prepare.
     */
    fun prepare(sourceLanguage: Language, targetLanguage: Language): Flow<Float>

    /** Number of cached translations (for the Settings storage display). */
    suspend fun cachedCount(): Int

    /** Clears all cached translations. */
    suspend fun clearCache()
}
