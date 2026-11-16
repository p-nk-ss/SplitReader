package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider

/** Stores per-provider translation API keys (encrypted at rest by the implementation). */
interface TranslatorKeyStore {
    fun getKey(provider: TranslationProvider): String?
    fun setKey(provider: TranslationProvider, value: String?)

    /** Deletes the key stored for LibreTranslate, a translator the app no longer has. */
    fun forgetLibreTranslate()
}
