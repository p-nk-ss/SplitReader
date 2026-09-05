package com.example.splitreader.data.translator

import com.example.splitreader.data.bergamot.BergamotEngine
import com.example.splitreader.data.bergamot.routeFor
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import com.example.splitreader.domain.translator.OfflineModelStore
import com.example.splitreader.domain.translator.TranslationProviderApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * "Offline HQ": Mozilla Firefox-Translations models run by bergamot-translator. Pairs touching
 * English use one model; anything else pivots through English (two models, two packs).
 */
class BergamotTranslationProvider(
    private val store: OfflineModelStore,
    private val engine: BergamotEngine,
    private val packDir: (ModelPair) -> File,
) : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.BERGAMOT

    /**
     * Spec §6: one native failure retires the provider for the rest of the process. The error copy
     * promises a fallback to ML Kit, and `resolveProvider` only delivers it while this reads false.
     */
    @Volatile private var sessionBroken = false

    override fun isConfigured(): Boolean = engine.available && !sessionBroken

    override fun supports(source: Language, target: Language): Boolean = engine.available && !sessionBroken

    override fun prepare(source: Language, target: Language): Flow<Float> = flow {
        val route = routeFor(source, target)
        route.forEachIndexed { i, pair ->
            store.ensure(pair).collect { p -> emit((i + p) / route.size) }
        }
    }

    override suspend fun translate(text: String, source: Language, target: Language): String {
        var current = text
        for (pair in routeFor(source, target)) {
            store.ensure(pair).collect { }   // no-op when installed; downloads if prepare() was skipped
            current = try {
                engine.translate(packDir(pair), pair.id, current)
            } catch (e: OfflineEngineUnavailableException) {
                sessionBroken = true
                throw e
            }
        }
        return current
    }
}
