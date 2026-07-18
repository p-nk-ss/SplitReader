package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider

/** Stores per-provider secondary translator config (LibreTranslate base URL / Azure region). */
interface TranslatorEndpointStore {
    fun getSecondary(provider: TranslationProvider): String
    fun setSecondary(provider: TranslationProvider, value: String?)
}
