package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language

/** Translates text between languages, caching results to avoid repeat work. */
interface TranslationRepository {
    suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String
}
