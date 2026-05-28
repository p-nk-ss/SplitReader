package com.example.splitreader.data.translator

import com.example.splitreader.data.translator.api.GoogleWebApi
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.TranslationProviderApi
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleWebTranslationProvider @Inject constructor(
    private val api: GoogleWebApi,
) : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.GOOGLE_WEB

    override fun isConfigured(): Boolean = true

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val raw = api.translate(
            sourceLang = source.code,
            targetLang = target.code,
            text = text,
        )
        return parseTranslation(raw)
    }

    private fun parseTranslation(raw: String): String {
        val root = JsonParser.parseString(raw)
        if (!root.isJsonArray) return raw
        val sentences = root.asJsonArray.firstOrNull()?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return raw
        val builder = StringBuilder()
        for (entry in sentences) {
            if (!entry.isJsonArray) continue
            val arr = entry.asJsonArray
            if (arr.isEmpty) continue
            val piece = arr[0]
            if (piece.isJsonPrimitive) builder.append(piece.asString)
        }
        return builder.toString().ifBlank { raw }
    }
}
