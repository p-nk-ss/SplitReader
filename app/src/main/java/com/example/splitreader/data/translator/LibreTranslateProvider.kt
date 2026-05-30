package com.example.splitreader.data.translator

import com.example.splitreader.data.local.ApiKeyManager
import com.example.splitreader.data.local.TranslatorEndpoints
import com.example.splitreader.data.translator.api.LibreTranslateApi
import com.example.splitreader.data.translator.api.LibreTranslateRequest
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.TranslationProviderApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibreTranslateProvider @Inject constructor(
    private val api: LibreTranslateApi,
    private val endpoints: TranslatorEndpoints,
    private val keys: ApiKeyManager,
) : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.LIBRE_TRANSLATE

    override fun isConfigured(): Boolean {
        if (keys.getLibreTranslateKey() != null) return true
        return endpoints.getLibreTranslateBaseUrl() != TranslatorEndpoints.DEFAULT_LIBRE_URL
    }

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val baseUrl = endpoints.getLibreTranslateBaseUrl().trimEnd('/')
        val response = api.translate(
            url = "$baseUrl/translate",
            body = LibreTranslateRequest(
                q = text,
                source = source.code,
                target = target.code,
                apiKey = keys.getLibreTranslateKey(),
            ),
        )
        return response.translatedText ?: error("Empty LibreTranslate response")
    }
}
