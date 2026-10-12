package com.example.splitreader.domain.model

sealed interface TranslationState {
    data object Idle : TranslationState
    data class DownloadingModel(val progress: Float? = null) : TranslationState
    data class Translating(val progress: Int) : TranslationState
    data class Partial(val index: Int, val text: String) : TranslationState

    /**
     * A translation failure.
     *
     * @property message Full text for the banner; may name its buttons (e.g. "tap Switch translator").
     * @property persistent Not cleared by scrolling — set for errors that won't self-resolve on retry
     *   (no/invalid API key); transient failures (network, rate limit) are not persistent.
     * @property short Text with no button references, for the popup; defaults to [message].
     */
    data class Error(
        val message: String,
        val persistent: Boolean = false,
        val short: String = message,
    ) : TranslationState

    /** The chosen provider can't serve this segment; [servedBy] translated it instead. */
    data class FallbackNotice(
        val requested: TranslationProvider,
        val servedBy: TranslationProvider,
        val reason: FallbackReason,
    ) : TranslationState
}
