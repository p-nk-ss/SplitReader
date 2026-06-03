package com.example.splitreader.domain.parser

import org.jsoup.Jsoup

/**
 * Builds the short "synopsis" teaser shown in the Library's Continue Reading hero.
 *
 * Prefers a real book description; falls back to the opening paragraph of the body.
 * Strips any HTML, collapses whitespace, and clamps to a teaser length so it stays
 * a few lines at most.
 */
object SynopsisExtractor {

    private const val MAX_LENGTH = 280
    private const val MIN_PARAGRAPH_LENGTH = 40
    private val WHITESPACE = Regex("\\s+")

    /** Strips HTML/whitespace, trims, clamps to [MAX_LENGTH]; returns null if blank. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = Jsoup.parse(raw).text().replace(WHITESPACE, " ").trim()
        if (text.isBlank()) return null
        return if (text.length <= MAX_LENGTH) {
            text
        } else {
            text.take(MAX_LENGTH).trimEnd().trimEnd(',', '.', ';', ':') + "…"
        }
    }

    /** [description] if present, else the first meaningful body paragraph — normalized. */
    fun build(description: String?, paragraphs: List<String>): String? =
        normalize(description)
            ?: normalize(paragraphs.firstOrNull { it.trim().length >= MIN_PARAGRAPH_LENGTH })
}
