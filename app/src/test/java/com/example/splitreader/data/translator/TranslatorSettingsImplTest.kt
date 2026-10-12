package com.example.splitreader.data.translator

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationProvider.AZURE
import com.example.splitreader.domain.model.TranslationProvider.DEEPL
import com.example.splitreader.domain.model.TranslationProvider.GOOGLE_CLOUD
import com.example.splitreader.domain.model.TranslationProvider.MLKIT
import com.example.splitreader.domain.model.TranslationUsage
import com.example.splitreader.domain.repository.TranslationUsageStats
import com.example.splitreader.domain.repository.TranslatorEndpointStore
import com.example.splitreader.domain.repository.TranslatorKeyStore
import com.example.splitreader.domain.translator.TranslationProviderApi
import com.example.splitreader.fakes.FakeReadingPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** One serialized module: writes land in order, the selection follows the key, config mirrors the stores. */
class TranslatorSettingsImplTest {
    private val log: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private inner class FakeKeyStore : TranslatorKeyStore {
        val keys = ConcurrentHashMap<TranslationProvider, String>()
        override fun getKey(provider: TranslationProvider): String? = keys[provider]
        override fun setKey(provider: TranslationProvider, value: String?) {
            log += "setKey $provider"
            if (value == null) keys.remove(provider) else keys[provider] = value
        }
    }

    private inner class FakeEndpoints : TranslatorEndpointStore {
        val values = ConcurrentHashMap<TranslationProvider, String>()
        override fun getSecondary(provider: TranslationProvider): String = values[provider] ?: ""
        override fun setSecondary(provider: TranslationProvider, value: String?) {
            log += "setSecondary $provider"
            if (value == null) values.remove(provider) else values[provider] = value
        }
    }

    private inner class FakeUsage : TranslationUsageStats {
        val chars = ConcurrentHashMap<TranslationProvider, Long>()
        override fun usage(provider: TranslationProvider) = TranslationUsage(chars[provider] ?: 0L, null)
        override fun reset(provider: TranslationProvider) {
            log += "reset $provider"
            chars.remove(provider)
        }
    }

    /** Key-requiring providers are configured iff the key store holds a key; the rest always are. */
    private class FakeProvider(override val id: TranslationProvider, val keys: FakeKeyStore) : TranslationProviderApi {
        override fun isConfigured() = !id.requiresApiKey || keys.getKey(id) != null
        override suspend fun translate(text: String, source: Language, target: Language) = text
    }

    private val keys = FakeKeyStore()
    private val endpoints = FakeEndpoints()
    private val usage = FakeUsage()

    private fun settings(prefs: FakeReadingPreferences, io: CoroutineDispatcher) = TranslatorSettingsImpl(
        keys = keys,
        endpoints = endpoints,
        usage = usage,
        prefs = prefs,
        providers = TranslationProvider.entries.associateWith { FakeProvider(it, keys) },
        io = io,
    )

    private fun TestScope.newSettings(current: TranslationProvider): Pair<FakeReadingPreferences, TranslatorSettingsImpl> {
        val prefs = FakeReadingPreferences(provider = current, log = log)
        return prefs to settings(prefs, StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `saveKey writes the key before selecting the provider`() = runTest {
        val (_, settings) = newSettings(MLKIT)

        settings.saveKey(DEEPL, "k", null)

        assertEquals(listOf("setKey DEEPL", "select DEEPL"), log.toList())
        assertEquals(DEEPL, settings.config.value.current)
        assertTrue(settings.config.value.configs[DEEPL]!!.configured)
    }

    @Test
    fun `clearing the current provider switches to ML Kit`() = runTest {
        keys.keys[DEEPL] = "k"
        val (_, settings) = newSettings(DEEPL)

        settings.clear(DEEPL)

        assertEquals(MLKIT, settings.config.value.current)
        assertFalse(settings.config.value.configs[DEEPL]!!.configured)
    }

    @Test
    fun `clearing another provider leaves the current one alone`() = runTest {
        keys.keys[DEEPL] = "k"
        keys.keys[AZURE] = "a"
        val (prefs, settings) = newSettings(DEEPL)

        settings.clear(AZURE)

        assertEquals(DEEPL, prefs.provider)
        assertEquals(DEEPL, settings.config.value.current)
        assertTrue(settings.config.value.configs[DEEPL]!!.configured)
        assertFalse(settings.config.value.configs[AZURE]!!.configured)
    }

    @Test
    fun `selecting the current provider writes nothing`() = runTest {
        val (_, settings) = newSettings(DEEPL)

        settings.select(DEEPL)

        assertEquals(emptyList<String>(), log.toList())
        assertEquals(DEEPL, settings.config.value.current)
    }

    @Test
    fun `config reflects the stores after every command`() = runTest {
        usage.chars[DEEPL] = 500L
        val (_, settings) = newSettings(MLKIT)
        settings.refresh()
        assertEquals(500L, settings.config.value.usage[DEEPL]!!.charactersThisMonth)

        settings.resetUsage(DEEPL)
        assertEquals(0L, settings.config.value.usage[DEEPL]!!.charactersThisMonth)

        keys.keys[DEEPL] = "set behind the module's back"
        settings.refresh()
        assertTrue(settings.config.value.configs[DEEPL]!!.configured)
    }

    /**
     * A race without the lock is rare per burst (about 1 run in 3 failed with a single burst), so the burst
     * is repeated [ROUNDS] times on fresh state and checked after each.
     */
    @Test
    fun `parallel commands leave config describing the selected provider`() = runBlocking {
        repeat(ROUNDS) { round ->
            keys.keys.clear()
            val prefs = FakeReadingPreferences(provider = MLKIT)
            val settings = settings(prefs, Dispatchers.Default)

            withContext(Dispatchers.Default) {
                coroutineScope {
                    repeat(20) {
                        launch { settings.select(DEEPL) }
                        launch { settings.select(AZURE) }
                        launch { settings.saveKey(GOOGLE_CLOUD, "k", null) }
                    }
                }
            }

            val config = settings.config.value
            assertEquals("round $round", prefs.provider, config.current)
            TranslationProvider.entries.forEach { p ->
                assertEquals("round $round: $p configured", !p.requiresApiKey || keys.keys[p] != null, config.configs[p]!!.configured)
            }
        }
    }

    private companion object {
        const val ROUNDS = 50
    }
}
