package com.example.splitreader.data.repository

import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.data.local.TranslationCacheEntity
import com.example.splitreader.data.local.TranslationDao
import com.example.splitreader.data.local.TranslationUsageTracker
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.TranslationProviderApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val providers: Map<TranslationProvider, @JvmSuppressWildcards TranslationProviderApi>,
    private val settings: ReadingProgressManager,
    private val dao: TranslationDao,
    private val usageTracker: TranslationUsageTracker,
) : TranslationRepository {

    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        val provider = resolveProvider(sourceLanguage, targetLanguage)
        val cacheKey = TranslationCacheKey.compute(provider.id, text, sourceLanguage, targetLanguage)
        dao.getCached(cacheKey)?.takeIf { it.originalText == text }?.let { return it.translatedText }
        val translated = provider.translate(text, sourceLanguage, targetLanguage)
        if (provider.id.tracksUsage) usageTracker.record(provider.id, text.length)
        dao.insert(TranslationCacheEntity(cacheKey, text, translated, targetLanguage.code))
        return translated
    }

    override suspend fun cachedCount(): Int = dao.count()

    override suspend fun clearCache() = dao.clearAll()

    private fun resolveProvider(source: Language, target: Language): TranslationProviderApi {
        val selected = settings.getTranslatorProvider()
        val candidate = providers[selected]
        if (candidate != null && candidate.isConfigured() && candidate.supports(source, target)) {
            return candidate
        }
        return providers[TranslationProvider.MLKIT]
            ?: error("MLKit translation provider is missing from DI")
    }
}
