package com.example.splitreader.domain.model

/**
 * Why a paragraph's translation isn't an ordinary translation from the book's language: it is in
 * another language (a quoted letter, a German line in an English novel, a Latin epigraph).
 */
sealed interface ParagraphNote {
    /** Translated from [language], not the book's language. */
    data class TranslatedFrom(val language: Language) : ParagraphNote

    /** Shown as it is: already in the translation's language, or in one that can't be translated ([code]). */
    data class Original(val code: String) : ParagraphNote

    /**
     * In [language], for which [provider]'s on-device model isn't downloaded: shown as it is until the
     * reader asks for the download (never started on its own: a pack can be 40 MB on mobile data).
     */
    data class NeedsModel(val language: Language, val provider: TranslationProvider) : ParagraphNote
}
