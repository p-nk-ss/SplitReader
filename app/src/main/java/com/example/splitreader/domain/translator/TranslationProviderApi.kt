package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface TranslationProviderApi {
    val id: TranslationProvider

    fun isConfigured(): Boolean

    fun supports(source: Language, target: Language): Boolean = true

    /** Optional pre-flight (e.g. pack download) with progress 0..1. Default: nothing to prepare. */
    fun prepare(source: Language, target: Language): Flow<Float> = emptyFlow()

    suspend fun translate(text: String, source: Language, target: Language): String
}
