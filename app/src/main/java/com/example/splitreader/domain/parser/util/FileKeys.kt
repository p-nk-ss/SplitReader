package com.example.splitreader.domain.parser.util

import java.security.MessageDigest

/**
 * Stable, collision-resistant id for a resource key (e.g. a book uri), used to name cover/image
 * files on disk. Replaces String.hashCode(), whose 32-bit collisions let different books overwrite
 * each other's cover. Returns the first 16 hex chars (64 bits) of the SHA-256 of [key].
 */
fun stableId(key: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    return buildString(16) { for (i in 0 until 8) append("%02x".format(digest[i])) }
}
