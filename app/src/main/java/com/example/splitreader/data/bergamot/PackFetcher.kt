package com.example.splitreader.data.bergamot

/** The one seam between the store and the network, so tests drive it with byte arrays. */
interface PackFetcher {
    /**
     * Streams [url] from byte [offset] (sends `Range: bytes=offset-`). Invokes [onChunk] with a buffer
     * and the valid length. Returns bytes delivered by this call. Throws IOException on failure.
     */
    suspend fun fetch(url: String, offset: Long, onChunk: suspend (ByteArray, Int) -> Unit): Long
}
