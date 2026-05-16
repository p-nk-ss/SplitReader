package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language

interface TranslationRepository {
    suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String
}
