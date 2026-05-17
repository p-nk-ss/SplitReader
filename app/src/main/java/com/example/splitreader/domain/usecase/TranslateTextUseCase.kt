package com.example.splitreader.domain.usecase

import com.example.splitreader.data.local.TranslationCacheEntity
import com.example.splitreader.data.local.TranslationDao
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.model.toTranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class TranslateTextUseCase @Inject constructor(
    private val dao: TranslationDao,
) {
    /**
     * Translates paragraphs in the given index range.
     * When endIndex < 0 the full list is translated (startIndex first, then wrapping around).
     */
    operator fun invoke(
        paragraphs: List<String>,
        sourceLanguage: Language,
        targetLanguage: Language,
        startIndex: Int = 0,
        endIndex: Int = -1,
    ): Flow<TranslationState> = flow {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage.toTranslateLanguage())
            .setTargetLanguage(targetLanguage.toTranslateLanguage())
            .build()
        val translator = Translation.getClient(options)

        try {
            emit(TranslationState.DownloadingModel)
            try {
                withTimeout(30_000L) { translator.downloadModelIfNeeded().await() }
            } catch (e: TimeoutCancellationException) {
                emit(TranslationState.Error("Model download timeout. Check internet connection."))
                return@flow
            }

            val clampedStart = startIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            val order: Iterable<Int> = if (endIndex >= 0) {
                clampedStart..endIndex.coerceIn(clampedStart, (paragraphs.size - 1).coerceAtLeast(0))
            } else {
                (clampedStart until paragraphs.size) + (0 until clampedStart)
            }

            for (index in order) {
                val paragraph = paragraphs[index]
                val cacheKey = "${paragraph.hashCode()}_${sourceLanguage.code}_${targetLanguage.code}"
                val translated = dao.getCached(cacheKey)?.translatedText ?: run {
                    val result = translator.translate(paragraph).await()
                    dao.insert(TranslationCacheEntity(cacheKey, paragraph, result, targetLanguage.code))
                    result
                }
                emit(TranslationState.Partial(index, translated))
            }
        } catch (e: Exception) {
            emit(TranslationState.Error(e.message ?: "Translation failed"))
        } finally {
            translator.close()
        }
    }.flowOn(Dispatchers.IO)
}
