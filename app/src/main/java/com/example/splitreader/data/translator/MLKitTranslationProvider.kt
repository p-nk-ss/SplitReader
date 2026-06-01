package com.example.splitreader.data.translator

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.toTranslateLanguage
import com.example.splitreader.domain.translator.ModelDownloadException
import com.example.splitreader.domain.translator.TranslationProviderApi
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Cap on the first-use on-device model download so a stalled download surfaces an error instead
 *  of an endless "Preparing translation…" banner. Once downloaded, the model is cached and reused. */
private const val MODEL_DOWNLOAD_TIMEOUT_MS = 90_000L

@Singleton
class MLKitTranslationProvider @Inject constructor() : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.MLKIT

    override fun isConfigured(): Boolean = true

    private val mutex = Mutex()
    private var cachedKey: Pair<String, String>? = null
    private var cachedTranslator: Translator? = null

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val translator = mutex.withLock { ensureTranslator(source, target) }
        val ready = withTimeoutOrNull(MODEL_DOWNLOAD_TIMEOUT_MS) {
            translator.downloadModelIfNeeded().await()
            true
        }
        if (ready == null) throw ModelDownloadException()
        return translator.translate(text).await()
    }

    private fun ensureTranslator(source: Language, target: Language): Translator {
        val key = source.toTranslateLanguage() to target.toTranslateLanguage()
        val existing = cachedTranslator
        if (existing != null && cachedKey == key) return existing

        existing?.close()
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(key.first)
                .setTargetLanguage(key.second)
                .build()
        )
        cachedTranslator = translator
        cachedKey = key
        return translator
    }
}
