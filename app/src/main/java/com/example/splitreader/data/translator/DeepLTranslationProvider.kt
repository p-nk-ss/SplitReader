package com.example.splitreader.data.translator

import com.example.splitreader.data.local.ApiKeyManager
import com.example.splitreader.data.translator.api.DeepLApi
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.TranslationProviderApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLTranslationProvider @Inject constructor(
    private val api: DeepLApi,
    private val keys: ApiKeyManager,
) : TranslationProviderApi {
    override val id: TranslationProvider = TranslationProvider.DEEPL

    override fun isConfigured(): Boolean = keys.getDeepLKey() != null

    override fun supports(source: Language, target: Language): Boolean {
        return source.toDeepLSourceCode() != null && target.toDeepLTargetCode() != null
    }

    override suspend fun translate(text: String, source: Language, target: Language): String {
        val key = keys.getDeepLKey()
            ?: throw IllegalStateException("Missing DeepL API key")
        val sourceCode = source.toDeepLSourceCode()
            ?: throw IllegalArgumentException("DeepL does not support source language: ${source.code}")
        val targetCode = target.toDeepLTargetCode()
            ?: throw IllegalArgumentException("DeepL does not support target language: ${target.code}")
        val response = api.translate(
            authHeader = "DeepL-Auth-Key $key",
            text = text,
            sourceLang = sourceCode,
            targetLang = targetCode,
        )
        return response.translations.firstOrNull()?.text ?: error("Empty DeepL response")
    }

    private fun Language.toDeepLSourceCode(): String? = when (this) {
        Language.ENGLISH -> "EN"
        Language.RUSSIAN -> "RU"
        Language.GERMAN -> "DE"
        Language.FRENCH -> "FR"
        Language.SPANISH -> "ES"
        Language.ITALIAN -> "IT"
        Language.CHINESE -> "ZH"
        Language.JAPANESE -> "JA"
        Language.PORTUGUESE -> "PT"
        Language.KOREAN -> "KO"
        Language.TURKISH -> "TR"
        Language.ARABIC -> "AR"
    }

    private fun Language.toDeepLTargetCode(): String? = when (this) {
        Language.ENGLISH -> "EN-US"
        Language.PORTUGUESE -> "PT-PT"
        else -> toDeepLSourceCode()
    }
}
