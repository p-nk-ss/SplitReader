package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationUsage

/** Reads and resets per-provider monthly translation usage. */
interface TranslationUsageStats {
    fun usage(provider: TranslationProvider): TranslationUsage
    fun reset(provider: TranslationProvider)
}
