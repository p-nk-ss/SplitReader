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

    /**
     * [bookDetectedAs] is what the whole book was detected as (a normalised tag) when the reader
     * chose another language for it: paragraphs detected as that are the book's too. Otherwise a
     * Belarusian book read as Russian would show every paragraph "not translated", and a book
     * misdetected as Spanish but read as Portuguese would be translated from Spanish after all.
     */
    suspend fun decide(text: String, book: Language, target: Language, bookDetectedAs: String? = null): Decision {
        if (text.trim().length < MIN_CHARS) return Decision.Book
        val tag = identify(text) ?: return Decision.Book
        if (bookDetectedAs != null && SourceDetection.normalizeTag(tag) == bookDetectedAs) return Decision.Book
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

    /**
     * The detector's answer for [text], remembered: a paragraph comes back on every scroll back,
     * chapter fill and language change, and asking ML Kit again each time is pure cost. Only the
     * start of a long paragraph is sent: a whole chapter as one paragraph says no more than that.
     */
    private suspend fun identify(text: String): String? {
        val sample = text.take(MAX_SAMPLE)
        synchronized(answers) { if (sample in answers) return answers[sample] }
        val tag = detector.identifyConfident(sample)
        synchronized(answers) { answers[sample] = tag }
        return tag
    }

    private val answers = object : LinkedHashMap<String, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > CACHE_SIZE
    }

    companion object {
        /** Shorter lines (replies, names, headings) are too short to tell a language by reliably. */
        const val MIN_CHARS = 60
        private const val MAX_SAMPLE = 1000
        private const val CACHE_SIZE = 2000

        /** Never reroutes: for callers built without a detector. */
        val NONE = ParagraphLanguage(object : LanguageDetector {
            override suspend fun identify(text: String): String? = null
        })
    }
}
