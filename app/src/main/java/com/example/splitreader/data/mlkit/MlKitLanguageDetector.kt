package com.example.splitreader.data.mlkit

import com.example.splitreader.domain.LanguageDetector
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitLanguageDetector @Inject constructor() : LanguageDetector {
    private val client by lazy { LanguageIdentification.getClient() }

    /** Answers "und" below [CONFIDENT]: a paragraph is only rerouted when ML Kit is sure. */
    private val confidentClient by lazy {
        LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(CONFIDENT).build())
    }

    override suspend fun identify(text: String): String? = identifyWith(client, text)

    override suspend fun identifyConfident(text: String): String? = identifyWith(confidentClient, text)

    private suspend fun identifyWith(c: LanguageIdentifier, text: String): String? =
        try {
            c.identifyLanguage(text).await().takeUnless { it == UNDETERMINED }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val UNDETERMINED = "und"
        const val CONFIDENT = 0.8f
    }
}
