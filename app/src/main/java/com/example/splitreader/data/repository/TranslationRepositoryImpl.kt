package com.example.splitreader.data.repository

import com.example.splitreader.data.local.TranslationCacheEntity
import com.example.splitreader.data.local.TranslationDao
import com.example.splitreader.data.local.TranslationUsageTracker
import com.example.splitreader.domain.model.FallbackReason
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.TranslationProviderApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val providers: Map<TranslationProvider, @JvmSuppressWildcards TranslationProviderApi>,
    private val dao: TranslationDao,
    private val usageTracker: TranslationUsageTracker,
) : TranslationRepository {

    override fun resolve(provider: TranslationProvider, source: Language, target: Language): Resolution {
        val api = providers[provider]
        val configured = api?.isConfigured() == true
        val supports = api?.supports(source, target) == true
        return when {
            api != null && configured && supports -> Resolution.Ready(provider)
            provider.requiresApiKey && !configured -> Resolution.NotConfigured(provider)
            !configured -> Resolution.Fallback(provider, mlKit(), FallbackReason.ENGINE_UNAVAILABLE)
            else -> Resolution.Fallback(provider, mlKit(), FallbackReason.UNSUPPORTED_PAIR)
        }
    }

    private fun mlKit(): TranslationProvider {
        check(providers.containsKey(TranslationProvider.MLKIT)) { "MLKit translation provider is missing from DI" }
        return TranslationProvider.MLKIT
    }

    override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
        val api = providers[provider] ?: error("$provider is missing from DI")
        val cacheKey = TranslationCacheKey.compute(provider, text, source, target)
        dao.getCached(cacheKey)?.takeIf { it.originalText == text }?.let { return it.translatedText }
        val translated = api.translate(text, source, target)
        if (provider.tracksUsage) usageTracker.record(provider, text.length)
        dao.insert(TranslationCacheEntity(cacheKey, text, translated, target.code))
        return translated
    }

    override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> =
        (providers[provider] ?: error("$provider is missing from DI")).prepare(source, target)

    override suspend fun cachedCount(): Int = dao.count()

    override suspend fun clearCache() = dao.clearAll()
}
