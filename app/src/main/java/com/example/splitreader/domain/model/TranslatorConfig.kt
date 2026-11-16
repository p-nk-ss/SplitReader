package com.example.splitreader.domain.model

data class ProviderConfig(
    val configured: Boolean,
    val secondaryValue: String = "",
    /**
     * Whether an API key is stored. Usually the same as [configured]; not for LibreTranslate, which
     * a self-hosted server configures without a key.
     */
    val keyStored: Boolean = configured,
)

data class TranslatorConfigState(
    val current: TranslationProvider,
    val configs: Map<TranslationProvider, ProviderConfig>,
    val usage: Map<TranslationProvider, TranslationUsage> = emptyMap(),
)
