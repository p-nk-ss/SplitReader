package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslatorConfigState
import kotlinx.coroutines.flow.StateFlow

interface TranslatorSettings {
    /** Current provider + per-provider configured/endpoint/usage. Reflects the stores after every command. */
    val config: StateFlow<TranslatorConfigState>
    suspend fun select(provider: TranslationProvider)
    /** Persists key and/or secondary (endpoint/region), THEN selects [provider]. Returns once both are durable. */
    suspend fun saveKey(provider: TranslationProvider, key: String?, secondary: String?)
    /** Removes the key; if [provider] is current, switches the selection to ML Kit. */
    suspend fun clear(provider: TranslationProvider)
    suspend fun resetUsage(provider: TranslationProvider)
    /** Re-reads usage counters and configured flags (e.g. when the picker opens). */
    suspend fun refresh()
}
