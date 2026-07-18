package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.ModelDownloadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Translates a range of paragraphs using the currently selected provider, emitting
 * [TranslationState] updates (model download, per-paragraph partials, or a friendly error).
 */
class TranslateTextUseCase @Inject constructor(
    private val repository: TranslationRepository,
    private val settings: ReadingPreferences,
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
        val provider = settings.getTranslatorProvider()
        if (provider == TranslationProvider.MLKIT) emit(TranslationState.DownloadingModel)

        val clampedStart = startIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        val order: Iterable<Int> = if (endIndex >= 0) {
            clampedStart..endIndex.coerceIn(clampedStart, (paragraphs.size - 1).coerceAtLeast(0))
        } else {
            (clampedStart until paragraphs.size) + (0 until clampedStart)
        }

        for (index in order) {
            val paragraph = paragraphs[index]
            try {
                val translated = repository.translate(paragraph, sourceLanguage, targetLanguage)
                emit(TranslationState.Partial(index, translated))
            } catch (e: Exception) {
                emit(TranslationState.Error(friendlyError(e, provider)))
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun friendlyError(e: Exception, provider: TranslationProvider): String = when {
        e is ModelDownloadException ->
            "Couldn't download the offline translation model. Check your internet and Google Play services, then retry."
        e is HttpException -> when (e.code()) {
            401, 403 -> "Invalid ${provider.displayName} API key — open Translator menu to update"
            429 -> "${provider.displayName} quota exceeded — try a different provider"
            else -> "${provider.displayName} error (${e.code()}): ${e.message()}"
        }
        e is IOException -> "No internet — switch to ML Kit for offline translation"
        else -> e.message ?: "Translation failed"
    }
}
