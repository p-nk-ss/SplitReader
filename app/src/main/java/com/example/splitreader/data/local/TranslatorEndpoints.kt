package com.example.splitreader.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    private fun normalize(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }

    companion object {
        const val DEFAULT_LIBRE_URL = "https://libretranslate.com"
        private const val KEY_LIBRE = "libre_base_url"
    }
}
