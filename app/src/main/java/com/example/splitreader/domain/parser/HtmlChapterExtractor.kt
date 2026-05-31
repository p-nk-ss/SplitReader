package com.example.splitreader.domain.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Shared HTML → chapter-content extraction used by every HTML-based format (EPUB, MOBI).
 *
 * Splits a single HTML document into a heading title plus three paragraph buckets:
 * content that appears *before* the chapter heading (a headless lead-in chapter),
 * epigraph/verse paragraphs, and the main body — applying the same heuristics across formats.
 */
object HtmlChapterExtractor {

    /** Substring markers used to heuristically detect epigraph/verse blocks by CSS class name. */
    private val EPIGRAPH_KEYWORDS = listOf(
        "epigraph", "epigraf",
        "poem", "stih", "verse", "stanza",
        "quote", "cite", "citation",
        "litany", "poetry",
    )

    data class Result(
        val headingTitle: String?,
        val preHeadingParagraphs: List<String>,
        val epigraphParagraphs: List<String>,
        val mainParagraphs: List<String>,
    ) {
        val isEmpty: Boolean
            get() = headingTitle == null &&
                preHeadingParagraphs.isEmpty() &&
                epigraphParagraphs.isEmpty() &&
                mainParagraphs.isEmpty()
    }

    /** Parses raw HTML bytes (with declared encoding fallback to UTF-8). */
    fun parse(content: ByteArray): Result = parse(Jsoup.parse(content.inputStream(), "UTF-8", ""))

    /** Parses an HTML/XHTML fragment or document supplied as a string. */
    fun parse(html: String): Result = parse(Jsoup.parse(html))

    private fun parse(doc: org.jsoup.nodes.Document): Result {
        val body = doc.body() ?: return Result(null, emptyList(), emptyList(), emptyList())
        val headingEl = body.selectFirst("h1, h2, h3")
        val headingTitle = headingEl?.text()?.takeIf { it.isNotBlank() }

        if (headingEl == null) {
            val paras = body.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
                .ifEmpty {
                    body.select("div, section, blockquote")
                        .map { it.ownText().trim() }.filter { it.length > 20 }
                }
            return Result(null, emptyList(), emptyList(), paras)
        }

        val preHeading = mutableListOf<String>()
        val epigraph = mutableListOf<String>()
        val main = mutableListOf<String>()
        var foundHeading = false

        for (el in body.select("h1, h2, h3, blockquote, p")) {
            val tag = el.tagName()
            when {
                tag in listOf("h1", "h2", "h3") -> foundHeading = true
                !foundHeading && tag == "p" && el.closest("blockquote") == null -> {
                    val text = el.text().trim()
                    if (text.isNotBlank()) preHeading.add(text)
                }
                tag == "blockquote" && foundHeading -> {
                    val texts = el.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
                        .ifEmpty { listOf(el.text().trim()).filter { it.isNotBlank() } }
                    epigraph.addAll(texts)
                }
                tag == "p" && foundHeading && el.closest("blockquote") == null -> {
                    val text = el.text().trim()
                    if (text.isBlank()) continue
                    if (looksLikeEpigraph(el)) epigraph.add(text) else main.add(text)
                }
            }
        }

        return Result(headingTitle, preHeading, epigraph, main)
    }

    private fun looksLikeEpigraph(el: Element): Boolean {
        fun Element.hasEpigraphClass() =
            classNames().any { c -> EPIGRAPH_KEYWORDS.any { k -> c.lowercase().contains(k) } }

        if (el.hasEpigraphClass()) return true
        val style = el.attr("style").lowercase()
        if ("italic" in style) return true
        // Check ancestor containers up to but not including body
        for (parent in el.parents()) {
            if (parent.tagName() in listOf("body", "html")) break
            if (parent.hasEpigraphClass()) return true
        }
        return false
    }
}
