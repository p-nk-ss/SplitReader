package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SourceDetection
import javax.inject.Inject

/**
 * Which language one paragraph is in, against the book's: a letter quoted in French, a German line
 * in an English novel, a Latin epigraph. Only a long enough paragraph the detector is sure about is
 * rerouted; anything else is the book's, so a short reply or a name never goes astray.
 */
class ParagraphLanguage @Inject constructor(
    private val detector: LanguageDetector,
) {
    sealed interface Decision {
        /** In the book's language: translated as usual. */
        data object Book : Decision

        /** Shown as it is: already in the target language, or in one that can't be translated ([code]). */
        data class Keep(val code: String) : Decision

        /** In another supported [language]: translated from it. */
        data class Other(val language: Language) : Decision
    }

    suspend fun decide(text: String, book: Language, target: Language): Decision {
        if (text.trim().length < MIN_CHARS) return Decision.Book
        val tag = detector.identifyConfident(text) ?: return Decision.Book
        return when (val detection = SourceDetection.fromTag(tag)) {
            is SourceDetection.Supported -> when (detection.language) {
                book -> Decision.Book
                target -> Decision.Keep(target.code)
                else -> Decision.Other(detection.language)
            }
            is SourceDetection.Unsupported -> Decision.Keep(detection.code)
            SourceDetection.Unknown -> Decision.Book
        }
    }

    companion object {
        /** Shorter lines (replies, names, headings) are too short to tell a language by reliably. */
        const val MIN_CHARS = 60

        /** Never reroutes: for callers built without a detector. */
        val NONE = ParagraphLanguage(object : LanguageDetector {
            override suspend fun identify(text: String): String? = null
        })
    }
}
