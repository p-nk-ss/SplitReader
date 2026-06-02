package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.model.TranslationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the user-configurable LibreTranslate base URL (falls back to a default). */
@Singleton
class TranslatorEndpoints @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("translator_endpoints", Context.MODE_PRIVATE)

    fun getLibreTranslateBaseUrl(): String =
        prefs.getString(KEY_LIBRE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LIBRE_URL

    fun setLibreTranslateBaseUrl(url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(KEY_LIBRE) else putString(KEY_LIBRE, normalize(url))
        }.apply()
    }

    fun getAzureRegion(): String =
        prefs.getString(KEY_AZURE_REGION, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_AZURE_REGION

    fun setAzureRegion(region: String?) {
        prefs.edit().apply {
            // Region is a plain Azure region id (e.g. "westeurope"), not a URL — store as-is.
            if (region.isNullOrBlank()) remove(KEY_AZURE_REGION)
            else putString(KEY_AZURE_REGION, region.trim().lowercase())
        }.apply()
    }

    /** Provider-keyed accessor for the secondary config value (region / base URL). */
    fun getSecondary(provider: TranslationProvider): String = when (provider) {
        TranslationProvider.AZURE -> getAzureRegion()
        TranslationProvider.LIBRE_TRANSLATE -> getLibreTranslateBaseUrl()
        else -> ""
    }

    /** Provider-keyed mutator; delegates to existing setters (preserving normalize/lowercase). */
    fun setSecondary(provider: TranslationProvider, value: String?) {
        when (provider) {
            TranslationProvider.AZURE -> setAzureRegion(value)
            TranslationProvider.LIBRE_TRANSLATE -> setLibreTranslateBaseUrl(value)
            else -> Unit
        }
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }

    companion object {
        const val DEFAULT_LIBRE_URL = "https://libretranslate.com"
        const val DEFAULT_AZURE_REGION = "global"
        private const val KEY_LIBRE = "libre_base_url"
        private const val KEY_AZURE_REGION = "azure_region"
    }
}
