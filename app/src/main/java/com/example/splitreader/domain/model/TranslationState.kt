package com.example.splitreader.domain.model

sealed interface TranslationState {
    data object Idle : TranslationState
    data object DownloadingModel : TranslationState
    data class Translating(val progress: Int) : TranslationState
    data class Partial(val index: Int, val text: String) : TranslationState
    data class Error(val message: String) : TranslationState
}
