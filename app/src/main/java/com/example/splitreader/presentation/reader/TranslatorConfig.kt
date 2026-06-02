package com.example.splitreader.presentation.reader

import androidx.compose.runtime.Immutable
import com.example.splitreader.data.local.TranslationUsage
import com.example.splitreader.data.local.TranslationUsageTracker
import com.example.splitreader.data.local.TranslatorEndpoints
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationProviderCategory
import com.example.splitreader.domain.translator.TranslationProviderApi

@Immutable
data class ProviderConfig(
    val configured: Boolean,
    val secondaryValue: String = "",
)

@Immutable
data class TranslatorConfigState(
    val current: TranslationProvider,
    val configs: Map<TranslationProvider, ProviderConfig>,
    val usage: Map<TranslationProvider, TranslationUsage> = emptyMap(),
)

/**
 * Single source of truth for the translator picker UI, built from the DI provider
 * registry + persisted stores. `configured` comes from each provider's own
 * isConfigured() so LibreTranslate's URL-based configured logic is preserved.
 */
fun buildTranslatorConfigState(
    providers: Map<TranslationProvider, @JvmSuppressWildcards TranslationProviderApi>,
    endpoints: TranslatorEndpoints,
    usageTracker: TranslationUsageTracker,
    current: TranslationProvider,
): TranslatorConfigState {
    val configs = TranslationProvider.entries.associateWith { p ->
        ProviderConfig(
            configured = providers[p]?.isConfigured() ?: (p.category == TranslationProviderCategory.FREE),
            secondaryValue = endpoints.getSecondary(p),
        )
    }
    return TranslatorConfigState(
        current = current,
        configs = configs,
        usage = TranslationProvider.entries.associateWith { usageTracker.usage(it) },
    )
}
