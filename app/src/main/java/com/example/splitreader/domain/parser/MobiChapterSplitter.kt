package com.example.splitreader.domain.parser

/**
 * Splits decompressed classic-MOBI HTML into per-chapter fragments. The primary signal is a page
 * break (`<mbp:pagebreak>` or an inline `page-break-before`); when a book has none — which would
 * otherwise collapse the whole book into one giant chapter — it falls back to splitting before each
 * heading of the shallowest level present. Pure and string-based; the split may cut through nesting,
 * but HtmlChapterExtractor (Jsoup) tolerates that and extracts text either way.
 */
object MobiChapterSplitter {

    private val PAGE_BREAK = Regex(
        "<\\s*mbp:pagebreak\\b[^>]*>|<[^>]*page-break-before\\s*:\\s*(?:always|left|right)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val HEADING_LEVELS = listOf("h1", "h2", "h3")

    fun split(html: String): List<String> {
        val byBreaks = PAGE_BREAK.split(html).map { it.trim() }.filter { it.isNotEmpty() }
        if (byBreaks.size >= 2) return byBreaks

        val level = HEADING_LEVELS.firstOrNull {
            Regex("<\\s*$it[\\s/>]", RegexOption.IGNORE_CASE).containsMatchIn(html)
        } ?: return listOf(html)

        val fragments = html.split(Regex("(?=<\\s*$level[\\s/>])", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotEmpty() }
        return fragments.ifEmpty { listOf(html) }
    }
}
