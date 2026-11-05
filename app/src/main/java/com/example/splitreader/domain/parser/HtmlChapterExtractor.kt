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

    /** An inline image reference. [src] is the raw HTML src/href; [anchorParagraph] indexes into the
     * FINAL chapter paragraph list (epigraph + main), before which the image renders. */
    data class ImageRef(
        val src: String,
        val anchorParagraph: Int,
        /** Produced before 2026-10 too (an image after the heading) — see ChapterImage.inLegacyLayout. */
        val inLegacyLayout: Boolean = false,
    )

    data class Result(
        val headingTitle: String?,
        val preHeadingParagraphs: List<String>,
        val epigraphParagraphs: List<String>,
        val mainParagraphs: List<String>,
        val imageRefs: List<ImageRef> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = headingTitle == null &&
                preHeadingParagraphs.isEmpty() &&
                epigraphParagraphs.isEmpty() &&
                mainParagraphs.isEmpty()

        /** No heading and no text, only images: a plate, cover or title page. */
        val isImageOnly: Boolean get() = isEmpty && imageRefs.isNotEmpty()
    }

    /** Parses raw HTML bytes (with declared encoding fallback to UTF-8). */
    fun parse(content: ByteArray): Result = parse(Jsoup.parse(content.inputStream(), "UTF-8", ""))

    /** Parses an HTML/XHTML fragment or document supplied as a string. */
    fun parse(html: String): Result = parse(Jsoup.parse(html))

    private fun parse(doc: org.jsoup.nodes.Document): Result {
        // Project Gutenberg boilerplate/license is wrapped in stable containers; remove it so only
        // the actual book text remains (no-op for non-Gutenberg HTML — selectors won't match).
        doc.select(
            "div.pg-boilerplate, #pg-header, #pg-footer, #project-gutenberg-license, " +
                "#pg-start-separator, #pg-end-separator",
        ).remove()
        val body = doc.body() ?: return Result(null, emptyList(), emptyList(), emptyList())
        val headingEl = body.selectFirst("h1, h2, h3")
        // Rows join the paragraphs only where the text lives in tables (see readsTables), and only
        // after a heading: a file with a heading was always one chapter, so the book's chapters stay
        // as they were. A headless table file, or rows before the heading, would add a chapter and
        // renumber every chapter after it — and chapter indices are saved too.
        val rows = if (headingEl != null && readsTables(body)) ", tr" else ""
        val headingTitle = headingEl?.text()?.takeIf { it.isNotBlank() }

        if (headingEl == null) {
            // Images count too: a chapter file without an h1–h3 (Gutenberg's illustrated "Alice"
            // has none) lost every image, and a file of only images was dropped whole.
            // A small image inside a paragraph of text is a glyph (a Gutenberg drop cap "C"), not an
            // illustration: keep its letter in the text instead of a full-width image after it.
            for (glyph in body.select("p img")) {
                val p = glyph.closest("p") ?: continue
                if (p.text().isBlank()) continue
                val alt = glyph.attr("alt").trim()
                val looksLikeGlyph = alt.length in 1..3 ||
                    glyph.classNames().any { c -> listOf("drop", "initial", "cap").any { it in c.lowercase() } }
                if (looksLikeGlyph) glyph.replaceWith(org.jsoup.nodes.TextNode(alt))
            }
            val paras = mutableListOf<String>()
            val refs = mutableListOf<ImageRef>()
            for (el in body.select("p, img, image")) {
                if (el.tagName() == "p") {
                    el.text().trim().takeIf { it.isNotBlank() }?.let(paras::add)
                } else {
                    imageSrc(el)?.let { refs.add(ImageRef(it, paras.size)) }
                }
            }
            if (paras.isEmpty()) {
                paras += body.select("div, section, blockquote")
                    .map { it.ownText().trim() }.filter { it.length > 20 }
            }
            return Result(null, emptyList(), emptyList(), paras, refs.map { it.copy(anchorParagraph = minOf(it.anchorParagraph, paras.size)) })
        }

        val preHeading = mutableListOf<String>()
        val epigraph = mutableListOf<String>()
        val main = mutableListOf<String>()
        // Anchors recorded in MAIN-relative space (count of main paragraphs so far); offset by
        // epigraph.size below since the final list is epigraph + main. Images before the heading
        // open the chapter (final anchor 0, ahead of any epigraph).
        val imageRefs = mutableListOf<ImageRef>()
        val leadingImageRefs = mutableListOf<ImageRef>()
        var foundHeading = false

        for (el in body.select("h1, h2, h3, blockquote, p, img, image$rows")) {
            val tag = el.tagName()
            when {
                tag in listOf("h1", "h2", "h3") -> foundHeading = true
                tag == "tr" -> if (foundHeading) rowText(el)?.let(main::add)
                (tag == "img" || tag == "image") && foundHeading -> {
                    imageSrc(el)?.let { imageRefs.add(ImageRef(it, main.size, inLegacyLayout = true)) }
                }
                tag == "img" || tag == "image" -> imageSrc(el)?.let { leadingImageRefs.add(ImageRef(it, 0)) }
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

        val finalImageRefs = leadingImageRefs + imageRefs.map { it.copy(anchorParagraph = epigraph.size + it.anchorParagraph) }
        return Result(headingTitle, preHeading, epigraph, main, finalImageRefs)
    }

    /**
     * Whether this document's text lives in tables: a play set as speaker | line rows (Gutenberg's
     * Czech R.U.R.), verse as line | number rows (the Greek Iliad). Their rows become paragraphs only
     * then — paragraph indices are saved (positions, bookmarks), so a table in an ordinary book
     * must add nothing; a book whose text is in tables showed (next to) nothing before, so its saved
     * positions have nothing to lose.
     */
    private fun readsTables(body: Element): Boolean {
        // Leaf cells only (Jsoup's select includes the element itself, hence size == 1): a cell
        // holding paragraphs or a nested table is counted through those.
        val inTables = body.select("td, th")
            .filter { it.select("p").isEmpty() && it.select("td, th").size == 1 }
            .sumOf { it.text().length }
        if (inTables == 0) return false
        val inParagraphs = body.select("p").sumOf { it.text().length }
        return inTables > inParagraphs
    }

    /**
     * A table row as one paragraph: its cells joined by " — " (speaker — line), with empty cells and
     * line numbers dropped. Null for a row of nothing but punctuation (a stanza break), a row holding
     * paragraphs of its own (they are read as paragraphs), or one holding a nested table.
     */
    private fun rowText(tr: Element): String? {
        if (tr.selectFirst("p, table") != null) return null
        val cells = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
            .map { it.text().trim() }
            .filter { cell -> cell.isNotEmpty() && !cell.all { it.isDigit() } }
        val text = cells.joinToString(" — ")
        return text.takeIf { t -> t.any { it.isLetterOrDigit() } }
    }

    /** Resolves the source of an <img> (src) or SVG <image> (any *href attribute), or null if absent. */
    private fun imageSrc(el: Element): String? {
        val raw = el.attr("src").ifBlank {
            el.attributes().firstOrNull { it.key.endsWith("href", ignoreCase = true) }?.value ?: ""
        }
        return raw.trim().takeIf { it.isNotBlank() }
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
