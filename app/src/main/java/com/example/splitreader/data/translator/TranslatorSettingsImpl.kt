package com.example.splitreader.data.translator

import com.example.splitreader.domain.IoDispatcher
import com.example.splitreader.domain.model.ProviderConfig
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationProviderCategory
import com.example.splitreader.domain.model.TranslatorConfigState
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.repository.TranslationUsageStats
import com.example.splitreader.domain.repository.TranslatorEndpointStore
import com.example.splitreader.domain.repository.TranslatorKeyStore
import com.example.splitreader.domain.repository.TranslatorSettings
import com.example.splitreader.domain.translator.TranslationProviderApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class TranslatorSettingsImpl @Inject constructor(
    private val keys: TranslatorKeyStore,
    private val endpoints: TranslatorEndpointStore,
    private val usage: TranslationUsageStats,
    private val prefs: ReadingPreferences,
    private val providers: Map<TranslationProvider, @JvmSuppressWildcards TranslationProviderApi>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TranslatorSettings {
    private val mutex = Mutex()
    private val _config = MutableStateFlow(
        TranslatorConfigState(current = prefs.getTranslatorProvider(), configs = emptyMap()),
    )
    override val config: StateFlow<TranslatorConfigState> = _config.asStateFlow()

    override suspend fun select(provider: TranslationProvider) = command {
        if (prefs.getTranslatorProvider() != provider) prefs.setTranslatorProvider(provider)
    }

    override suspend fun saveKey(provider: TranslationProvider, key: String?, secondary: String?) = command {
        if (key != null) keys.setKey(provider, key)
        if (provider.secondaryLabel != null && secondary != null) endpoints.setSecondary(provider, secondary)
        prefs.setTranslatorProvider(provider)
    }

    override suspend fun clear(provider: TranslationProvider) = command {
        keys.setKey(provider, null)
        if (prefs.getTranslatorProvider() == provider) prefs.setTranslatorProvider(TranslationProvider.MLKIT)
    }

    override suspend fun resetUsage(provider: TranslationProvider) = command { usage.reset(provider) }

    override suspend fun refresh() = command { }

    /** Every write and the rebuild happen under one lock, off the main thread: a stale config can't be published. */
    private suspend fun command(write: () -> Unit) = withContext(io) {
        mutex.withLock {
            write()
            _config.value = build(prefs.getTranslatorProvider())
        }
    }

    /**
     * Built from the DI provider registry + persisted stores. `configured` comes from each provider's
     * own isConfigured() so LibreTranslate's URL-based configured logic is preserved.
     */
    private fun build(current: TranslationProvider): TranslatorConfigState {
        val configs = TranslationProvider.entries.associateWith { p ->
            ProviderConfig(
                configured = providers[p]?.isConfigured() ?: (p.category == TranslationProviderCategory.FREE),
                secondaryValue = endpoints.getSecondary(p),
            )
        }
        return TranslatorConfigState(
            current = current,
            configs = configs,
            usage = TranslationProvider.entries.associateWith { usage.usage(it) },
        )
    }
}
