package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The engine owns the only native handles in the process. Rules: at most two models resident
 * (a pivot needs two), least-recently-used goes first, trim drops everything and the next call
 * reloads, and a missing library means "unavailable", never a crash.
 */
class BergamotEngineTest {
    private class FakeBridge(
        private val failLoadFor: Set<String> = emptySet(),
        failFirstLoadFor: Set<String> = emptySet(),
        private val onTranslate: () -> Unit = {},
    ) : NativeBridge {
        private val failOnce = failFirstLoadFor.toMutableSet()
        val loads = mutableListOf<String>()
        val unloads = mutableListOf<Long>()
        val translates = mutableListOf<Pair<Long, String>>()
        private var next = 1L
        private val byHandle = mutableMapOf<Long, String>()
        override fun load(configYaml: String): Long {
            val key = Regex("/([a-z]{2}-[a-z]{2})/").find(configYaml)!!.groupValues[1]
            loads += key
            if (key in failLoadFor) return 0
            if (failOnce.remove(key)) return 0
            return (next++).also { byHandle[it] = key }
        }
        override fun translate(handle: Long, text: String): String? {
            onTranslate()
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

    /**
     * The Application field-injects the engine, so resolving the bridge in the constructor would
     * put System.loadLibrary on the main thread at every cold start. The library must not be
     * touched until something actually translates — and onTrimMemory must not be what triggers it.
     */
    @Test
    fun `unloadAll before first use never loads the library`() = runTest(dispatcher) {
        var resolved = 0
        val e = BergamotEngine({ resolved++; FakeBridge() }, dispatcher)

        e.unloadAll()
        assertEquals("trimming an untouched engine must not load the native library", 0, resolved)

        e.translate(dir("en-ru"), "en-ru", "hi")
        assertEquals("the first translation resolves the bridge exactly once", 1, resolved)
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

    /**
     * onTrimMemory arrives while a translation holds the lock: `tryLock` misses, and nothing else
     * will call back. The request must be remembered and honoured by the translation on its way out,
     * or a trim during the one moment memory is actually tight is the one trim that does nothing.
     */
    @Test
    fun `unloadAll during a translation is honoured when that translation finishes`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val b = FakeBridge(onTranslate = { entered.countDown(); release.await(5, TimeUnit.SECONDS) })
        val executor = Executors.newSingleThreadExecutor()
        val engineDispatcher = executor.asCoroutineDispatcher()
        val e = BergamotEngine(b, engineDispatcher)
        try {
            runBlocking {
                val job = launch(Dispatchers.IO) { e.translate(dir("en-ru"), "en-ru", "hi") }
                assertTrue("translation never started", entered.await(5, TimeUnit.SECONDS))
                e.unloadAll()               // lock is held → tryLock misses
                release.countDown()
                job.join()
            }
        } finally {
            executor.shutdown()
        }
        assertEquals("a missed trim must not be forgotten", emptyList<String>(), e.loadedKeys)
        assertEquals(listOf(1L), b.unloads)
    }

    /**
     * Spec §6: a failed load is read as "two models did not fit". Evicting and retrying rescues the
     * call, but the device has just told us it cannot hold two — so the ceiling drops to one for the
     * rest of the session rather than walking into the same wall on the next pivot.
     */
    @Test
    fun `a load failure drops the resident ceiling to one for the session`() = runTest(dispatcher) {
        val b = FakeBridge(failFirstLoadFor = setOf("en-ru"))
        val e = BergamotEngine(b, dispatcher)

        assertEquals("the retry must rescue the call", "en-ru:a", e.translate(dir("en-ru"), "en-ru", "a"))
        assertTrue("the engine must report itself degraded", e.degraded)

        e.translate(dir("en-de"), "en-de", "b")
        assertEquals("a degraded engine keeps one model, not two", listOf("en-de"), e.loadedKeys)
    }
}
