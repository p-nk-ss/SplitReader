package com.example.splitreader.data.mlkit

import com.example.splitreader.domain.LanguageDetector
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitLanguageDetector @Inject constructor() : LanguageDetector {
    private val client by lazy { LanguageIdentification.getClient() }

    override suspend fun identify(text: String): String? =
        try {
            client.identifyLanguage(text).await().takeUnless { it == UNDETERMINED }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val UNDETERMINED = "und"
    }
}
