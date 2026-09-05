package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.InsufficientStorageException
import com.example.splitreader.domain.translator.ModelDownloadException
import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import com.example.splitreader.domain.translator.OfflinePackCorruptException
import com.example.splitreader.domain.translator.OfflinePackDownloadException
import com.example.splitreader.domain.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
        // Offline engines may have to fetch a model/pack first; the banner shows that wait, and a
        // failure here is the user's error to see (an unreported pack download looks like a hang).
        if (!provider.requiresNetwork) {
            emit(TranslationState.DownloadingModel())
            try {
                repository.prepare(sourceLanguage, targetLanguage).collect {
                    emit(TranslationState.DownloadingModel(it))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(TranslationState.Error(friendlyError(e, provider)))
                return@flow
            }
        }

        val clampedStart = startIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        val order: Iterable<Int> = if (endIndex >= 0) {
            clampedStart..endIndex.coerceIn(clampedStart, (paragraphs.size - 1).coerceAtLeast(0))
        } else {
            (clampedStart until paragraphs.size) + (0 until clampedStart)
        }

        // One flaky request must not strand the rest of the window untranslated, so failures skip
        // the paragraph and keep going (the planner re-issues unmarked paragraphs later). A run of
        // consecutive failures means the provider is down — give up rather than serially time out.
        var firstError: Exception? = null
        var consecutiveFailures = 0
        for (index in order) {
            val paragraph = paragraphs[index]
            try {
                val translated = translateWithRateLimitRetry(paragraph, sourceLanguage, targetLanguage)
                emit(TranslationState.Partial(index, translated))
                consecutiveFailures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break
            }
        }
        firstError?.let { emit(TranslationState.Error(friendlyError(it, provider))) }
    }.flowOn(ioDispatcher)

    /**
     * A 429 is a *temporary* per-IP rate limit (the unofficial Quick Translate endpoint trips it on
     * bursts, shared carrier IPs, or VPNs), so the request is retried after a short wait before the
     * paragraph counts as failed. Other errors are not retried: quota/key problems won't self-heal,
     * and a dead network is better surfaced fast than serially timed out per retry.
     */
    private suspend fun translateWithRateLimitRetry(
        text: String,
        source: Language,
        target: Language,
    ): String {
        var attempt = 0
        while (true) {
            try {
                return repository.translate(text, source, target)
            } catch (e: HttpException) {
                if (e.code() != 429 || attempt >= RATE_LIMIT_BACKOFF_MS.size) throw e
                delay(RATE_LIMIT_BACKOFF_MS[attempt])
                attempt++
            }
        }
    }

    private fun friendlyError(e: Exception, provider: TranslationProvider): String = when {
        e is ModelDownloadException ->
            "Couldn't download the offline translation model. Check your internet and Google Play services, then retry."
        e is OfflinePackDownloadException ->
            "Couldn't download the Offline HQ language pack (${e.pair.source.code}→${e.pair.target.code}, " +
                "${e.bytes / 1_000_000} MB). Check your internet and retry."
        e is OfflinePackCorruptException ->
            "The Offline HQ pack was corrupted and could not be re-downloaded."
        e is InsufficientStorageException ->
            "Not enough storage for the Offline HQ pack (needs ${e.neededBytes / 1_000_000} MB free)."
        e is OfflineEngineUnavailableException ->
            "Offline HQ isn't available on this device — using ML Kit."
        e is HttpException -> when (e.code()) {
            401, 403 -> "Invalid ${provider.displayName} API key — open Translator menu to update"
            // Quick Translate has no quota to exhaust — its 429 is the free endpoint asking for a
            // pause, and "quota exceeded" sends users hunting for a limit that doesn't exist.
            429 -> if (provider == TranslationProvider.QUICK_TRANSLATE) {
                "${provider.displayName} is temporarily rate-limited — wait a minute or switch to ML Kit"
            } else {
                "${provider.displayName} quota exceeded — try a different provider"
            }
            else -> "${provider.displayName} error (${e.code()}): ${e.message()}"
        }
        e is IOException -> "No internet — switch to ML Kit for offline translation"
        else -> e.message ?: "Translation failed"
    }

    private companion object {
        const val MAX_CONSECUTIVE_FAILURES = 3

        /** Waits before re-trying a 429-rejected request; length of the list caps the retries. */
        val RATE_LIMIT_BACKOFF_MS = longArrayOf(1_000, 3_000)
    }
}
