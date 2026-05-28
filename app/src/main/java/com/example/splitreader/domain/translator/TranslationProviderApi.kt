package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider

interface TranslationProviderApi {
    val id: TranslationProvider

    fun isConfigured(): Boolean

    fun supports(source: Language, target: Language): Boolean = true

    suspend fun translate(text: String, source: Language, target: Language): String
}
