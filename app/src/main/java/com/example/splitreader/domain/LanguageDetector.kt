package com.example.splitreader.domain

import com.example.splitreader.domain.model.Language
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class LanguageDetector @Inject constructor() {

    suspend fun detectLanguage(text: String): Language =
        try {
            val langCode = LanguageIdentification.getClient()
                .identifyLanguage(text.take(500))
                .await()
            Language.entries.find { it.code == langCode } ?: Language.ENGLISH
        } catch (e: Exception) {
            Language.ENGLISH
        }
}
