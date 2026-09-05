package com.example.splitreader.data.bergamot

/**
 * Raw JNI surface of `libbergamot_jni.so` (built by `tools/bergamot/build.sh`).
 *
 * Not thread-safe by contract — the native `BlockingService` and `TranslationModel` it wraps are
 * not either, so every call has to be serialised onto a single thread by the caller.
 */
object BergamotNative {
    /** Loads a model from a marian-decoder YAML config. Returns 0 on failure; see [lastError]. */
    external fun load(configYaml: String): Long

    /** Translates [text] with the model behind [handle]. Returns null on failure; see [lastError]. */
    external fun translate(handle: Long, text: String): String?

    /** Releases the model behind [handle]. */
    external fun unload(handle: Long)

    /** The message of the most recent native failure, or "" if there has not been one. */
    external fun lastError(): String

    const val LIBRARY = "bergamot_jni"
}
