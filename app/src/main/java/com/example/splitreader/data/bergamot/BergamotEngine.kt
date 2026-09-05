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
    maxLoaded: Int = 2,
) {
    /**
     * Not a val: spec §6 reads a failed load as "that many models did not fit on this device", so
     * the ceiling drops to one for the rest of the session rather than hitting the same wall on
     * every subsequent pivot. Only ever touched under [mutex].
     */
    private var maxLoaded: Int = maxLoaded

    constructor(bridge: NativeBridge?, dispatcher: CoroutineDispatcher, maxLoaded: Int = 2) :
        this({ bridge }, dispatcher, maxLoaded)

    private val bridgeLazy: Lazy<NativeBridge?> = lazy(bridgeProvider)
    private val bridge: NativeBridge? get() = bridgeLazy.value

    /** Resolves the library on first read, so never call this from the main thread. */
    val available: Boolean get() = bridge != null

    private val mutex = Mutex()
    private val loaded = LinkedHashMap<String, Long>()   // insertion order == LRU order (oldest first)

    val loadedKeys: List<String> get() = loaded.keys.toList()

    @Volatile private var degradedFlag = false

    /**
     * True once a load has failed and the ceiling dropped to one model. A failure reported *after*
     * this is terminal — there is no smaller configuration left to retry in — which is what lets
     * [com.example.splitreader.data.translator.BergamotTranslationProvider] tell a transient OOM
     * (recoverable, keep the provider) from a genuinely broken engine (retire it for the session).
     */
    val degraded: Boolean get() = degradedFlag

    /**
     * Set when [unloadAll] finds the lock held. The translation holding it drops everything on its
     * way out instead: `tryLock` missing is exactly the moment memory is tight, so a skipped trim
     * is the one trim that mattered. Benign race: a request arriving between the check and the
     * unlock is honoured by the *next* translation, costing one extra reload.
     */
    @Volatile private var pendingUnload = false

    suspend fun translate(packDir: File, key: String, text: String): String {
        val b = bridge ?: throw OfflineEngineUnavailableException("native library not loaded")
        return withContext(dispatcher) {
            mutex.withLock {
                try {
                    val handle = loaded.remove(key) ?: run {
                        while (loaded.size >= maxLoaded) {
                            val eldest = loaded.entries.first()
                            b.unload(eldest.value); loaded.remove(eldest.key)
                        }
                        var h = b.load(bergamotConfigYaml(packDir))
                        if (h == 0L) {
                            // Spec §6: a failed load (typically OOM with the ceiling's worth of
                            // models resident) drops everything, halves the ambition to a single
                            // resident model for the rest of the session, and retries once.
                            loaded.values.forEach(b::unload); loaded.clear()
                            maxLoaded = 1
                            degradedFlag = true
                            h = b.load(bergamotConfigYaml(packDir))
                        }
                        if (h == 0L) throw OfflineEngineUnavailableException("load $key: ${b.lastError()}")
                        h
                    }
                    loaded[key] = handle   // re-insert → most recently used
                    b.translate(handle, text)
                        ?: throw OfflineEngineUnavailableException("translate $key: ${b.lastError()}")
                } finally {
                    // Still under the lock: a trim that missed tryLock is honoured here.
                    if (pendingUnload) {
                        pendingUnload = false
                        loaded.values.forEach(b::unload); loaded.clear()
                    }
                }
            }
        }
    }

    /** Called from onTrimMemory; safe from any thread (synchronous unload under the same lock). */
    fun unloadAll() {
        // Nothing can be resident before the bridge exists, and trimming must never be the thing
        // that drags the native library in — least of all on the main thread.
        if (!bridgeLazy.isInitialized()) return
        val b = bridge ?: return
        // tryLock: a translation mid-flight owns the lock, so we hand the job to it via
        // pendingUnload rather than skipping — memory pressure may not call again.
        if (mutex.tryLock()) {
            try { loaded.values.forEach(b::unload); loaded.clear() } finally { mutex.unlock() }
        } else {
            pendingUnload = true
        }
    }
}
