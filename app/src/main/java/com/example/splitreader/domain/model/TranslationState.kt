package com.example.splitreader.domain.model

sealed interface TranslationState {
    data object Idle : TranslationState
    data class DownloadingModel(val progress: Float? = null) : TranslationState
    data class Translating(val progress: Int) : TranslationState
    data class Partial(val index: Int, val text: String) : TranslationState
    data class Error(val message: String) : TranslationState

    /** The chosen provider can't serve this segment; [servedBy] translated it instead. */
    data class FallbackNotice(
        val requested: TranslationProvider,
        val servedBy: TranslationProvider,
        val reason: FallbackReason,
    ) : TranslationState
}
