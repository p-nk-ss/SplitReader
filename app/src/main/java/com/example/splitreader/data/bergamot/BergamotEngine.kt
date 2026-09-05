package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns native model handles. All native calls run on [dispatcher] (a single thread in production)
 * and under one mutex — the native side is not thread-safe. Holds at most [maxLoaded] models, LRU.
 *
 * [bridgeProvider] is resolved lazily, on first use: the Application field-injects this engine, so
 * loading the native library in the constructor would put `System.loadLibrary` (and the dlopen of
 * an 8 MB .so) on the main thread of every cold start, for every user, whether or not they ever
 * pick Offline HQ. Callers must therefore touch [available] off the main thread.
 */
class BergamotEngine(
    bridgeProvider: () -> NativeBridge?,
    private val dispatcher: CoroutineDispatcher,
    private val maxLoaded: Int = 2,
) {
    constructor(bridge: NativeBridge?, dispatcher: CoroutineDispatcher, maxLoaded: Int = 2) :
        this({ bridge }, dispatcher, maxLoaded)

    private val bridgeLazy: Lazy<NativeBridge?> = lazy(bridgeProvider)
    private val bridge: NativeBridge? get() = bridgeLazy.value

    /** Resolves the library on first read, so never call this from the main thread. */
    val available: Boolean get() = bridge != null

    private val mutex = Mutex()
    private val loaded = LinkedHashMap<String, Long>()   // insertion order == LRU order (oldest first)

    val loadedKeys: List<String> get() = loaded.keys.toList()

    suspend fun translate(packDir: File, key: String, text: String): String {
        val b = bridge ?: throw OfflineEngineUnavailableException("native library not loaded")
        return withContext(dispatcher) {
            mutex.withLock {
                val handle = loaded.remove(key) ?: run {
                    while (loaded.size >= maxLoaded) {
                        val eldest = loaded.entries.first()
                        b.unload(eldest.value); loaded.remove(eldest.key)
                    }
                    var h = b.load(bergamotConfigYaml(packDir))
                    if (h == 0L) {
                        // Spec §6: a failed load (typically OOM with two models resident) drops
                        // everything and retries once with an empty engine before giving up.
                        loaded.values.forEach(b::unload); loaded.clear()
                        h = b.load(bergamotConfigYaml(packDir))
                    }
                    if (h == 0L) throw OfflineEngineUnavailableException("load $key: ${b.lastError()}")
                    h
                }
                loaded[key] = handle   // re-insert → most recently used
                b.translate(handle, text) ?: throw OfflineEngineUnavailableException("translate $key: ${b.lastError()}")
            }
        }
    }

    /** Called from onTrimMemory; safe from any thread (synchronous unload under the same lock). */
    fun unloadAll() {
        // Nothing can be resident before the bridge exists, and trimming must never be the thing
        // that drags the native library in — least of all on the main thread.
        if (!bridgeLazy.isInitialized()) return
        val b = bridge ?: return
        // tryLock: if a translation is mid-flight we skip; memory pressure will call again.
        if (mutex.tryLock()) {
            try { loaded.values.forEach(b::unload); loaded.clear() } finally { mutex.unlock() }
        }
    }
}
