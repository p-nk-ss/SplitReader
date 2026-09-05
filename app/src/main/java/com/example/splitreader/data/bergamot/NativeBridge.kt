package com.example.splitreader.data.bergamot

/** Fakeable face of [BergamotNative]. */
interface NativeBridge {
    fun load(configYaml: String): Long
    fun translate(handle: Long, text: String): String?
    fun unload(handle: Long)
    fun lastError(): String
}

class JniNativeBridge : NativeBridge {
    override fun load(configYaml: String) = BergamotNative.load(configYaml)
    override fun translate(handle: Long, text: String) = BergamotNative.translate(handle, text)
    override fun unload(handle: Long) = BergamotNative.unload(handle)
    override fun lastError() = BergamotNative.lastError()

    companion object {
        /** Null when the library cannot be loaded on this device (wrong ABI, broken install). */
        fun loadOrNull(onFailure: (Throwable) -> Unit): JniNativeBridge? =
            runCatching { System.loadLibrary(BergamotNative.LIBRARY); JniNativeBridge() }
                .onFailure(onFailure).getOrNull()
    }
}
