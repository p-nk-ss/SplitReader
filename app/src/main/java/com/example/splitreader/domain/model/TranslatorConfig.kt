package com.example.splitreader.domain.model

data class ProviderConfig(
    val configured: Boolean,
    val secondaryValue: String = "",
)

data class TranslatorConfigState(
    val current: TranslationProvider,
    val configs: Map<TranslationProvider, ProviderConfig>,
    val usage: Map<TranslationProvider, TranslationUsage> = emptyMap(),
)
