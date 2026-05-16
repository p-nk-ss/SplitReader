package com.example.splitreader.data.repository

import com.example.splitreader.data.local.TranslationCacheEntity
import com.example.splitreader.data.local.TranslationDao
import com.example.splitreader.domain.model.Language
import javax.inject.Singleton
import com.example.splitreader.domain.repository.TranslationRepository
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val dao: TranslationDao
) : TranslationRepository {

    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        val cacheKey = "${text.hashCode()}_${sourceLanguage.code}_${targetLanguage.code}"
        dao.getCached(cacheKey)?.let { return it.translatedText }

        val translated = translateWithMlKit(text, sourceLanguage.code, targetLanguage.code)
        dao.insert(TranslationCacheEntity(cacheKey, text, translated, targetLanguage.code))
        return translated
    }

    private suspend fun translateWithMlKit(text: String, sourceCode: String, targetCode: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(targetCode)
            .build()
        val translator = Translation.getClient(options)
        try {
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
                cont.invokeOnCancellation { translator.close() }
            }
            return suspendCancellableCoroutine { cont ->
                translator.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
                cont.invokeOnCancellation { translator.close() }
            }
        } finally {
            translator.close()
        }
    }
}
