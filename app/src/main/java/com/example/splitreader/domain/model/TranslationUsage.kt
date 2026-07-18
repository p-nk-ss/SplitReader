package com.example.splitreader.domain.model

/** Monthly translation-character usage for a provider, for quota display. */
data class TranslationUsage(
    val charactersThisMonth: Long,
    val monthlyLimit: Long?,
)
