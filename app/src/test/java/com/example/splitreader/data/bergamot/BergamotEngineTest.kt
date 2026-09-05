package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The engine owns the only native handles in the process. Rules: at most two models resident
 * (a pivot needs two), least-recently-used goes first, trim drops everything and the next call
 * reloads, and a missing library means "unavailable", never a crash.
 */
class BergamotEngineTest {
    private class FakeBridge(private val failLoadFor: Set<String> = emptySet()) : NativeBridge {
        val loads = mutableListOf<String>()
        val unloads = mutableListOf<Long>()
        val translates = mutableListOf<Pair<Long, String>>()
        private var next = 1L
        private val byHandle = mutableMapOf<Long, String>()
        override fun load(configYaml: String): Long {
            val key = Regex("/([a-z]{2}-[a-z]{2})/").find(configYaml)!!.groupValues[1]
            loads += key
            if (key in failLoadFor) return 0
            return (next++).also { byHandle[it] = key }
        }
        override fun translate(handle: Long, text: String): String? {
            translates += handle to text
            return "${byHandle[handle]}:$text"
        }
        override fun unload(handle: Long) { unloads += handle; byHandle.remove(handle) }
        override fun lastError() = "fake error"
    }

    private fun dir(key: String) = File("/packs/$key")
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `translates through the loaded model`() = runTest(dispatcher) {
        val e = BergamotEngine(FakeBridge(), dispatcher)
        assertEquals("en-ru:hi", e.translate(dir("en-ru"), "en-ru", "hi"))
    }

    @Test
    fun `keeps two models and evicts the least recently used third`() = runTest(dispatcher) {
        val b = FakeBridge()
        val e = BergamotEngine(b, dispatcher)
        e.translate(dir("ru-en"), "ru-en", "a")
        e.translate(dir("en-de"), "en-de", "b")
        e.translate(dir("ru-en"), "ru-en", "c")     // touch ru-en → en-de is now LRU
        e.translate(dir("en-fr"), "en-fr", "d")
        assertEquals(listOf("ru-en", "en-fr"), e.loadedKeys)
        assertEquals(listOf(2L), b.unloads)         // en-de had handle 2
        assertEquals(3, b.loads.size)
    }

    @Test
    fun `unloadAll drops everything and the next call reloads`() = runTest(dispatcher) {
        val b = FakeBridge()
        val e = BergamotEngine(b, dispatcher)
        e.translate(dir("en-ru"), "en-ru", "a")
        e.unloadAll()
        assertEquals(emptyList<String>(), e.loadedKeys)
        e.translate(dir("en-ru"), "en-ru", "b")
        assertEquals(listOf("en-ru", "en-ru"), b.loads)
    }

    @Test
    fun `missing library means unavailable and no bridge calls`() = runTest(dispatcher) {
        val e = BergamotEngine(bridge = null, dispatcher = dispatcher)
        assertFalse(e.available)
        val err = runCatching { e.translate(dir("en-ru"), "en-ru", "x") }.exceptionOrNull()
        assertTrue(err is OfflineEngineUnavailableException)
    }

    @Test
    fun `native load failure surfaces as unavailable exception with the native message`() = runTest(dispatcher) {
        val e = BergamotEngine(FakeBridge(failLoadFor = setOf("en-ru")), dispatcher)
        val err = runCatching { e.translate(dir("en-ru"), "en-ru", "x") }.exceptionOrNull()
        assertTrue(err is OfflineEngineUnavailableException)
        assertTrue(err!!.message!!.contains("fake error"))
    }

    @Test
    fun `a failed load evicts resident models and retries once`() = runTest(dispatcher) {
        val b = FakeBridge(failLoadFor = setOf("en-fr"))
        val e = BergamotEngine(b, dispatcher)
        e.translate(dir("en-ru"), "en-ru", "a")
        runCatching { e.translate(dir("en-fr"), "en-fr", "b") }
        assertEquals(listOf("en-ru", "en-fr", "en-fr"), b.loads)
        assertEquals(listOf(1L), b.unloads)
        assertEquals(emptyList<String>(), e.loadedKeys)
    }
}
