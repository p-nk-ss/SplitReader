package com.example.splitreader.data.bergamot

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * The store has no stall detector of its own for a silent socket, so [client] must carry a read
 * timeout (the app's shared OkHttpClient sets 30 s) — that timeout is what bounds a dead connection.
 */
class OkHttpPackFetcher @Inject constructor(private val client: OkHttpClient) : PackFetcher {
    override suspend fun fetch(url: String, offset: Long, onChunk: suspend (ByteArray, Int) -> Unit): Long {
        val request = Request.Builder().url(url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            // A server ignoring Range replies 200 with the whole body; the caller must then restart from 0.
            if (offset > 0 && response.code != 206) throw RangeNotSupported()
            val body = response.body ?: throw IOException("empty body for $url")
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    onChunk(buffer, n)
                    total += n
                }
            }
            return total
        }
    }
}

class RangeNotSupported : IOException("server ignored Range")
