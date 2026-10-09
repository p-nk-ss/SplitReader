package com.example.splitreader.domain

import com.example.splitreader.domain.model.Language

/** Detects a text's language; ENGLISH when unsure or on failure. Implemented with ML Kit in data/mlkit. */
interface LanguageDetector {
    suspend fun detectLanguage(text: String): Language
}
