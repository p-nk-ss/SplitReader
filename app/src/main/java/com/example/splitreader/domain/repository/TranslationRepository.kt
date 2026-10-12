package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import kotlinx.coroutines.flow.Flow

/** Translates text between languages with an explicitly chosen provider, caching results to avoid repeat work. */
interface TranslationRepository {
    /**
     * Which engine serves [source]→[target] for [provider]: the provider itself, an announced ML Kit
     * fallback, or nothing when the provider's API key isn't set. Never swaps engines silently.
     */
    fun resolve(provider: TranslationProvider, source: Language, target: Language): Resolution

    /** Translates [text] with exactly [provider] (callers pass the engine [resolve] chose to serve). */
    suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String

    /**
     * Pre-flight for [provider] (offline packs download here). Emits progress 0f..1f and completes
     * when the provider is ready; empty for providers with nothing to prepare.
     */
    fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float>

    /** Number of cached translations (for the Settings storage display). */
    suspend fun cachedCount(): Int

    /** Clears all cached translations. */
    suspend fun clearCache()
}
