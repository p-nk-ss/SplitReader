package com.example.splitreader.domain.model

/** What a book is written in, as detected from its text. */
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
}
