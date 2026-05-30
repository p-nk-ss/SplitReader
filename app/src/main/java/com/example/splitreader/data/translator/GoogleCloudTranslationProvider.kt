package com.example.splitreader.data.translator

import com.example.splitreader.data.local.ApiKeyManager
import com.example.splitreader.data.translator.api.GoogleCloudApi
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.TranslationProviderApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCloudTranslationProvider @Inject constructor(
    private val api: GoogleCloudApi,
    private val keys: ApiKeyManager,
) : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.GOOGLE_CLOUD

    override fun isConfigured(): Boolean = keys.getGoogleCloudKey() != null

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val key = keys.getGoogleCloudKey()
            ?: throw IllegalStateException("Missing Google Cloud API key")
        val response = api.translate(
            apiKey = key,
            text = text,
            source = source.toGoogleCloudCode(),
            target = target.toGoogleCloudCode(),
        )
        return response.data?.translations?.firstOrNull()?.translatedText
            ?: error("Empty Google Cloud response")
    }

    private fun Language.toGoogleCloudCode(): String = when (this) {
        Language.CHINESE -> "zh-CN"
        else -> code
    }
}
