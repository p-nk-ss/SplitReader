package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider

/** Stores per-provider secondary translator config (Azure's region). */
interface TranslatorEndpointStore {
    fun getSecondary(provider: TranslationProvider): String
    fun setSecondary(provider: TranslationProvider, value: String?)

    /** Deletes the server address stored for LibreTranslate, a translator the app no longer has. */
    fun forgetLibreTranslate()
}
