package com.example.splitreader.domain.translator

import java.io.IOException

/** The pack could not be fetched (network). [bytes] is the pack's uncompressed size for the message. */
class OfflinePackDownloadException(val pair: ModelPair, val bytes: Long, cause: Throwable? = null) :
    IOException("Offline pack ${pair.id} download failed", cause)

/** Hash mismatch after download and after the single automatic retry. */
class OfflinePackCorruptException(val pair: ModelPair) :
    IOException("Offline pack ${pair.id} is corrupted")

class InsufficientStorageException(val neededBytes: Long) :
    IOException("Need $neededBytes bytes free")

/** libbergamot_jni.so failed to load or a native call failed; the provider is unavailable this session. */
class OfflineEngineUnavailableException(detail: String) :
    IOException("Offline HQ engine unavailable: $detail")
