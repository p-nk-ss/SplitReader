package com.example.splitreader.domain.model

/** What a book (or a paragraph) is written in, as detected from its text. */
sealed interface SourceDetection {
    /** One of the languages the app translates from. */
    data class Supported(val language: Language) : SourceDetection

    /**
     * A language the app can't translate from, by its tag as the detector reports it, lowercased:
     * "fi", "la", a three-letter one ("fil"), or romanised text of a supported language ("hi-latn").
     */
    data class Unsupported(val code: String) : SourceDetection

    /** No language could be told from the text (no prose, or the detector failed). */
    data object Unknown : SourceDetection

    companion object {
        /** Supported languages not written in Latin script: their "-Latn" text is romanised. */
        private val NON_LATIN_SCRIPT = setOf(
            Language.UKRAINIAN, Language.RUSSIAN, Language.CHINESE, Language.JAPANESE,
            Language.KOREAN, Language.ARABIC, Language.HINDI,
        )

        /**
         * A detector tag reduced to what matters for translation: its language ("de-Latn" → "de"),
         * except romanised text of a language written in another script ("hi-Latn"), which stays as
         * it is: the translators expect Hindi in Devanagari, so to them it is unsupported.
         */
        fun normalizeTag(tag: String): String {
            val lower = tag.lowercase()
            val base = lower.substringBefore('-')
            val romanised = lower.endsWith("-latn") && Language.fromCodeOrNull(base) in NON_LATIN_SCRIPT
            return if (romanised) lower else base
        }

        /** A detector tag as a detection: one of the 18 languages, or an unsupported one. */
        fun fromTag(tag: String): SourceDetection {
            val normalized = normalizeTag(tag)
            return Language.fromCodeOrNull(normalized)?.let { Supported(it) } ?: Unsupported(normalized)
        }
    }
}
