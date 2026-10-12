package com.example.splitreader.domain.model

/** Which engine will actually translate a pair for the provider the reader chose (no silent swaps). */
sealed interface Resolution {
    data class Ready(val provider: TranslationProvider) : Resolution
    /** The chosen provider can't serve this pair/device; [servedBy] translates instead, and the UI says so. */
    data class Fallback(
        val requested: TranslationProvider,
        val servedBy: TranslationProvider,
        val reason: FallbackReason,
    ) : Resolution
    /** The chosen provider needs an API key that isn't set. Nothing is translated. */
    data class NotConfigured(val provider: TranslationProvider) : Resolution
}

enum class FallbackReason { UNSUPPORTED_PAIR, ENGINE_UNAVAILABLE }
