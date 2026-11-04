package com.example.splitreader.domain

/**
 * Identifies a text's language. Implemented with ML Kit in data/mlkit.
 *
 * Returns the language's BCP-47 code as identified ("de", "fi", "zh-Latn", …), whether or not the app
 * can translate it, or null when it can't tell or fails. Deciding what a book is written in, and
 * whether that is a language the app supports, is [com.example.splitreader.domain.usecase.DetectBookLanguageUseCase]'s job.
 */
interface LanguageDetector {
    suspend fun identify(text: String): String?

    /**
     * Like [identify], but null unless the detector is sure: for single paragraphs, where a wrong
     * answer would show a paragraph untranslated or translate it from the wrong language.
     */
    suspend fun identifyConfident(text: String): String? = null
}
