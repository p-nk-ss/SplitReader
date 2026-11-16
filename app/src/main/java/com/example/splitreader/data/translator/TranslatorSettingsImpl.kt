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

    init {
        retireLibreTranslate()
    }

    private val _config = MutableStateFlow(
        TranslatorConfigState(current = current(), configs = emptyMap(), notice = prefs.getTranslatorNotice()),
    )
    override val config: StateFlow<TranslatorConfigState> = _config.asStateFlow()

    override suspend fun select(provider: TranslationProvider) = command {
        if (current() != provider) prefs.setTranslatorProvider(provider)
        dropNotice()
    }

    override suspend fun saveKey(provider: TranslationProvider, key: String?, secondary: String?) = command {
        if (key != null) keys.setKey(provider, key)
        if (provider.secondaryLabel != null && secondary != null) endpoints.setSecondary(provider, secondary)
        prefs.setTranslatorProvider(provider)
        dropNotice()
    }

    /** A reader who chose a translator needs no notice about the one chosen for them (it would name the wrong one). */
    private fun dropNotice() {
        if (prefs.getTranslatorNotice() != null) prefs.setTranslatorNotice(null)
    }

    /**
     * Forgets the provider's key. Azure's region stays (its key alone configures it; a reset region
     * would fail the next key with 401/403).
     */
    override suspend fun clear(provider: TranslationProvider) = command {
        keys.setKey(provider, null)
        if (current() == provider) prefs.setTranslatorProvider(TranslationProvider.MLKIT)
    }

    override suspend fun resetUsage(provider: TranslationProvider) = command { usage.reset(provider) }

    override suspend fun refresh() = command { }

    override suspend fun noticeShown() = command { prefs.setTranslatorNotice(null) }

    /**
     * LibreTranslate was removed in v29. A reader who had chosen it gets the default translator and
     * one notice saying so -- never a silent switch. Whatever was stored for it (key, server) is
     * deleted for everyone. Its usage counter is left: nothing reads it.
     */
    private fun retireLibreTranslate() {
        keys.forgetLibreTranslate()
        endpoints.forgetLibreTranslate()
        if (prefs.retiredTranslatorProvider() == RETIRED_LIBRE_TRANSLATE) {
            // The notice first: both are async writes to one prefs file, each writing the whole file,
            // so a cleared choice on disk always comes with its notice. Lost both -> this runs again.
            // (The retired name already reads as "no choice", so current() is the default here.)
            prefs.setTranslatorNotice("LibreTranslate is no longer supported — switched to ${current().displayName}.")
            prefs.clearTranslatorProvider()
        }
    }

    /**
     * The reader's choice, or — never chosen — Offline HQ, the best on-device engine (it covers all
     * 18 languages). ML Kit only where Offline HQ can't run (its native library is 64-bit only).
     */
    private fun current(): TranslationProvider =
        prefs.getTranslatorProvider()
            ?: if (providers[TranslationProvider.BERGAMOT]?.isConfigured() == true) TranslationProvider.BERGAMOT
            else TranslationProvider.MLKIT

    /** Every write and the rebuild happen under one lock, off the main thread: a stale config can't be published. */
    private suspend fun command(write: () -> Unit) = withContext(io) {
        mutex.withLock {
            write()
            _config.value = build(current())
        }
    }

    /** Built from the DI provider registry + persisted stores; `configured` is each provider's own isConfigured(). */
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
            notice = prefs.getTranslatorNotice(),
        )
    }

    private companion object {
        /** The name versions before v29 stored for LibreTranslate. */
        const val RETIRED_LIBRE_TRANSLATE = "LIBRE_TRANSLATE"
    }
}
