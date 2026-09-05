package com.example.splitreader.data.translator

import com.example.splitreader.data.bergamot.BergamotEngine
import com.example.splitreader.data.bergamot.BergamotManifest
import com.example.splitreader.data.bergamot.ManifestFile
import com.example.splitreader.data.bergamot.ManifestPack
import com.example.splitreader.data.bergamot.NativeBridge
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.InstalledPack
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import com.example.splitreader.domain.translator.OfflineModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Provider = route × store × engine. Direct pairs use one model, others two; prepare() averages progress. */
class BergamotTranslationProviderTest {
    private class FakeStore : OfflineModelStore {
        val ensured = mutableListOf<ModelPair>()
        override fun isInstalled(pair: ModelPair) = true
        override fun ensure(pair: ModelPair): Flow<Float> { ensured += pair; return flowOf(0.5f, 1f) }
        override suspend fun delete(pair: ModelPair) = Unit
        override fun installed(): Flow<List<InstalledPack>> = flowOf(emptyList())
    }
    private class EchoBridge : NativeBridge {
        private var next = 1L; private val keys = mutableMapOf<Long, String>()
        override fun load(configYaml: String): Long {
            val key = Regex("/([a-z]{2}-[a-z]{2})/").find(configYaml)!!.groupValues[1]
            return (next++).also { keys[it] = key }
        }
        override fun translate(handle: Long, text: String) = "[${keys[handle]}]$text"
        override fun unload(handle: Long) { keys.remove(handle) }
        override fun lastError() = ""
    }
    private val dispatcher = StandardTestDispatcher()

    /** Every en↔lang pair the app supports, which is what the pinned manifest actually ships. */
    private fun manifestOf(pairs: List<Pair<String, String>>) = BergamotManifest(
        version = 1, baseUrl = "https://bucket",
        packs = pairs.map { (s, t) ->
            ManifestPack(s, t, "base-memory", ManifestFile("m"), ManifestFile("v"), null, ManifestFile("l"))
        },
    )

    private fun fullManifest() = manifestOf(
        Language.entries.filter { it != Language.ENGLISH }
            .flatMap { listOf("en" to it.code, it.code to "en") },
    )

    private fun provider(
        store: FakeStore = FakeStore(),
        bridge: NativeBridge? = EchoBridge(),
        manifest: BergamotManifest = fullManifest(),
    ) = BergamotTranslationProvider(store, BergamotEngine(bridge, dispatcher), manifest) { File("/packs/${it.id}") }

    @Test
    fun `direct pair runs one model`() = runTest(dispatcher) {
        assertEquals("[en-ru]hello", provider().translate("hello", Language.ENGLISH, Language.RUSSIAN))
    }

    @Test
    fun `non-English pair pivots through English`() = runTest(dispatcher) {
        assertEquals("[en-de][ru-en]привет", provider().translate("привет", Language.RUSSIAN, Language.GERMAN))
    }

    @Test
    fun `prepare ensures every pack on the route and averages progress`() = runTest(dispatcher) {
        val store = FakeStore()
        val progress = provider(store).prepare(Language.RUSSIAN, Language.GERMAN).toList()
        assertEquals(listOf(ModelPair(Language.RUSSIAN, Language.ENGLISH), ModelPair(Language.ENGLISH, Language.GERMAN)), store.ensured)
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), progress)
    }

    @Test
    fun `unavailable engine reports not configured and supports nothing`() {
        val p = provider(bridge = null)
        assertFalse(p.isConfigured())
        assertFalse(p.supports(Language.ENGLISH, Language.RUSSIAN))
    }

    /**
     * Spec §6: after a native failure the provider must stay unavailable for the session, so the
     * error copy ("— using ML Kit.") is actually true — resolveProvider then falls back on the
     * very next paragraph instead of failing again through a broken engine.
     */
    @Test
    fun `a native failure takes the provider out of service for the session`() = runTest(dispatcher) {
        val deadBridge = object : NativeBridge {
            override fun load(configYaml: String) = 0L
            override fun translate(handle: Long, text: String): String? = null
            override fun unload(handle: Long) = Unit
            override fun lastError() = "dlopen failed"
        }
        val p = provider(bridge = deadBridge)
        assertTrue("engine looks usable until it actually fails", p.isConfigured())

        val err = runCatching { p.translate("hello", Language.ENGLISH, Language.RUSSIAN) }.exceptionOrNull()

        assertTrue("$err", err is OfflineEngineUnavailableException)
        assertFalse("a broken engine must not be offered again", p.isConfigured())
        assertFalse("nor claim to support the pair it just failed", p.supports(Language.ENGLISH, Language.RUSSIAN))
    }

    @Test
    fun `available engine supports every pair in the language enum`() {
        val p = provider()
        for (s in Language.entries) for (t in Language.entries) if (s != t) assertTrue("${s.code}→${t.code}", p.supports(s, t))
    }

    /**
     * Spec §6's retirement is for a *broken* engine, not a bad minute. A single failed native call
     * on an otherwise healthy engine must not cost the reader the provider for the whole session —
     * the engine still has room to work in, so the next paragraph gets another go.
     */
    @Test
    fun `a recoverable native failure leaves the provider in service`() = runTest(dispatcher) {
        val flaky = object : NativeBridge {
            private var next = 1L; private val keys = mutableMapOf<Long, String>(); private var failedOnce = false
            override fun load(configYaml: String): Long {
                val key = Regex("/([a-z]{2}-[a-z]{2})/").find(configYaml)!!.groupValues[1]
                return (next++).also { keys[it] = key }
            }
            override fun translate(handle: Long, text: String): String? {
                if (!failedOnce) { failedOnce = true; return null }
                return "[${keys[handle]}]$text"
            }
            override fun unload(handle: Long) { keys.remove(handle) }
            override fun lastError() = "transient"
        }
        val p = provider(bridge = flaky)

        val err = runCatching { p.translate("hello", Language.ENGLISH, Language.RUSSIAN) }.exceptionOrNull()
        assertTrue("$err", err is OfflineEngineUnavailableException)

        assertTrue("one bad call must not retire a healthy engine", p.isConfigured())
        assertEquals("[en-ru]hello", p.translate("hello", Language.ENGLISH, Language.RUSSIAN))
    }

    /**
     * The pivot needs *both* legs. Claiming support for a pair the pinned manifest cannot supply
     * sends the reader into a download that ends in "not in manifest" instead of quietly leaving
     * the pair to ML Kit.
     */
    @Test
    fun `supports only pairs whose whole route is in the manifest`() {
        val p = provider(manifest = manifestOf(listOf("en" to "de", "de" to "en", "ru" to "en")))
        assertFalse("en-ru is missing", p.supports(Language.ENGLISH, Language.RUSSIAN))
        assertTrue(p.supports(Language.ENGLISH, Language.GERMAN))
        assertTrue("ru→de pivots through en and both legs exist", p.supports(Language.RUSSIAN, Language.GERMAN))
        assertFalse("de→ru needs the missing en-ru leg", p.supports(Language.GERMAN, Language.RUSSIAN))
    }
}
