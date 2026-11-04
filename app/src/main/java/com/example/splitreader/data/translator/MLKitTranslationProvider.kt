package com.example.splitreader.data.translator

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.toTranslateLanguage
import com.example.splitreader.domain.translator.ModelDownloadException
import com.example.splitreader.domain.translator.TranslationProviderApi
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    override suspend fun isReady(source: Language, target: Language): Boolean = try {
        val models = RemoteModelManager.getInstance()
        listOf(source, target).all { lang ->
            models.isModelDownloaded(TranslateRemoteModel.Builder(lang.toTranslateLanguage()).build()).await()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private val mutex = Mutex()
    private var cachedKey: Pair<String, String>? = null
    private var cachedTranslator: Translator? = null

    /**
     * Downloads both models (ML Kit reports no progress: one step to 1f); also what the reader's
     * "Download and translate" runs. Its own client: the cached one is closed whenever the pair
     * changes, and the reader's download runs while paragraphs translate in the book's pair.
     */
    override fun prepare(source: Language, target: Language): Flow<Float> = flow {
        // Every segment and popup lookup prepares first: with both models here there is nothing
        // to do, and emitting would flash the banner's "Preparing… 100%".
        if (isReady(source, target)) return@flow
        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source.toTranslateLanguage())
                .setTargetLanguage(target.toTranslateLanguage())
                .build()
        )
        try {
            download(client)
        } finally {
            client.close()
        }
        emit(1f)
    }

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val translator = mutex.withLock { ensureTranslator(source, target) }
        download(translator)
        return translator.translate(text).await()
    }

    private suspend fun download(translator: Translator) {
        val ready = withTimeoutOrNull(MODEL_DOWNLOAD_TIMEOUT_MS) {
            translator.downloadModelIfNeeded().await()
            true
        }
        if (ready == null) throw ModelDownloadException()
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
