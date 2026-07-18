package com.example.splitreader.data.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import java.security.MessageDigest

/**
 * Builds a collision-resistant cache key for a translation. The text is hashed with SHA-256
 * (hex) rather than [String.hashCode], whose 32-bit space collides across a book and could
 * otherwise return another paragraph's cached translation for the same provider/language pair.
 */
object TranslationCacheKey {

    fun compute(
        provider: TranslationProvider,
        text: String,
        source: Language,
        target: Language,
    ): String = "${provider.name}_${sha256Hex(text)}_${source.code}_${target.code}"

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
