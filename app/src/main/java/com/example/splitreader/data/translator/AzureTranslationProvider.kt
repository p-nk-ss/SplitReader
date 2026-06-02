package com.example.splitreader.data.translator

import com.example.splitreader.data.local.ApiKeyManager
import com.example.splitreader.data.local.TranslatorEndpoints
import com.example.splitreader.data.translator.api.AzureTranslateItem
import com.example.splitreader.data.translator.api.AzureTranslatorApi
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.TranslationProviderApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AzureTranslationProvider @Inject constructor(
    private val api: AzureTranslatorApi,
    private val endpoints: TranslatorEndpoints,
    private val keys: ApiKeyManager,
) : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.AZURE

    override fun isConfigured(): Boolean = keys.getAzureKey() != null

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val key = keys.getAzureKey()
            ?: throw IllegalStateException("Missing Azure Translator API key")
        // Global resources authenticate with the key alone; region-bound resources also need the
        // region header. Send it only when the user configured a non-global region.
        val region = endpoints.getAzureRegion()
            .takeIf { it.isNotBlank() && !it.equals("global", ignoreCase = true) }
        val response = api.translate(
            from = source.toAzureCode(),
            to = target.toAzureCode(),
            key = key,
            region = region,
            body = listOf(AzureTranslateItem(text)),
        )
        return response.firstOrNull()?.translations?.firstOrNull()?.text
            ?: error("Empty Azure response")
    }

    private fun Language.toAzureCode(): String = when (this) {
        Language.CHINESE -> "zh-Hans"
        else -> code
    }
}
