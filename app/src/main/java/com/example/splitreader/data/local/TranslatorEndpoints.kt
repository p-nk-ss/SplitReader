package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslatorEndpointStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists Azure's region, the one translator setting besides a key. */
@Singleton
class TranslatorEndpoints @Inject constructor(
    @ApplicationContext context: Context,
) : TranslatorEndpointStore {
    private val prefs = context.getSharedPreferences("translator_endpoints", Context.MODE_PRIVATE)

    fun getAzureRegion(): String =
        prefs.getString(KEY_AZURE_REGION, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_AZURE_REGION

    fun setAzureRegion(region: String?) {
        prefs.edit().apply {
            // Region is a plain Azure region id (e.g. "westeurope"), not a URL — store as-is.
            if (region.isNullOrBlank()) remove(KEY_AZURE_REGION)
            else putString(KEY_AZURE_REGION, region.trim().lowercase())
        }.apply()
    }

    /** Provider-keyed accessor for the secondary config value (Azure's region). */
    override fun getSecondary(provider: TranslationProvider): String = when (provider) {
        TranslationProvider.AZURE -> getAzureRegion()
        else -> ""
    }

    /** Provider-keyed mutator; delegates to existing setters (preserving normalize/lowercase). */
    override fun setSecondary(provider: TranslationProvider, value: String?) {
        when (provider) {
            TranslationProvider.AZURE -> setAzureRegion(value)
            else -> Unit
        }
    }

    override fun forgetLibreTranslate() {
        prefs.edit().remove(KEY_RETIRED_LIBRE).apply()
    }

    companion object {
        const val DEFAULT_AZURE_REGION = "global"
        private const val KEY_AZURE_REGION = "azure_region"
        /** Where versions before v29 kept the LibreTranslate server; only ever deleted now. */
        private const val KEY_RETIRED_LIBRE = "libre_base_url"
    }
}
