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

    /** h1–h6: the FIRST h1–h3 in a file is the chapter heading (unchanged); every other heading
     * tag — later h1–h3, or h4–h6 anywhere — is read as an ordinary paragraph (see [addReadableText]). */
    private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

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
        // Decided ONCE per document (not per row/cell — see findContentsTables): which tables are
        // tables of contents or plate lists, so none of their rows is text to read.
        val contentsTables = if (headingEl != null) findContentsTables(body) else emptySet()
        // Decided ONCE per document, same spirit, for <ul>/<ol>: a list where (nearly) every item is
        // a link elsewhere (Don Quixote's list-formatted tables of contents) is never text to read.
        val tocLists = findTocLists(body)
        // Rows join the paragraphs only where the text lives in tables (see readsTables), and only
        // after a heading: a file with a heading was always one chapter, so the book's chapters stay
        // as they were. A headless table file, or rows before the heading, would add a chapter and
        // renumber every chapter after it — and chapter indices are saved too.
        val rows = if (headingEl != null && readsTables(body, contentsTables)) ", tr" else ""
        val headingTitle = headingEl?.text()?.takeIf { it.isNotBlank() }

        // A small image inside a paragraph of text is a glyph (a Gutenberg drop cap "C"), not an
        // illustration: keep its letter in the text instead of a full-width image after it. Applies
        // everywhere (heading or heading-less file) since it only ever touches text already inside a
        // <p> with other content.
        for (glyph in body.select("p img")) {
            val p = glyph.closest("p") ?: continue
            if (p.text().isBlank()) continue
            if (looksLikeGlyph(glyph)) glyph.replaceWith(org.jsoup.nodes.TextNode(glyph.attr("alt").trim()))
        }

        // Candidates in document order: every block-level text type read across BOTH a file with a
        // heading and a heading-less one — headings, <p>, <li>, <pre>, <dd>/<dt>, and leaf div/section
        // text blocks (some Gutenberg editions set a chapter's text in divs, not <p> — Gutenberg's
        // illustrated "Alice", ebook 28885 — and the SAME thing happens inside a file that DOES have a
        // heading, e.g. Alice's own chapters 12–13: div.unindent after a drop cap, div.poem). A wrapper
        // div/section around one of these never matches isLeafTextBlock itself (it has a block-level
        // descendant), so nothing is read twice; an element inside a <li> is read once, through that
        // <li>'s own text (see [blockText]), never separately too.
        val candidates = body.select("h1, h2, h3, h4, h5, h6, p, li, pre, dd, dt, div, section, img, image")

        // A drop cap standing alone in its own container (Alice's <div class="figleft"><img
        // alt="A"/></div>, no sibling text or element — an in-<p> glyph is handled above instead),
        // immediately followed by the next real text block whose own text starts with a letter: merge
        // the alt into that block instead of emitting an image ("A" + "LICE was…" = "ALICE was…"),
        // mirroring the in-<p> glyph rule. This runs over the SAME shared candidate list used by both
        // paths below, so the merge applies whether or not the file has a heading. Whether a space
        // belongs between the alt and the block's text depends on the source: Chapter I's drop cap is
        // mid-word ("A" + "LICE" = "ALICE", no space in the source), but Chapter VIII's is the whole
        // word "A" followed by "   LARGE rose-tree…" (3 literal spaces before the next word) —
        // Element.text() normalizes that leading whitespace away entirely, so [startsWithRawWhitespace]
        // reads the first real TextNode's unnormalized wholeText instead. mergedAlt accumulates in
        // document order in case more than one such glyph precedes the same next block.
        val mergedAlt = mutableMapOf<Element, String>()
        val skipImages = mutableSetOf<Element>()
        for ((idx, el) in candidates.withIndex()) {
            if (!isImageTag(el) || !isStandaloneGlyphContainer(el) || !looksLikeGlyph(el)) continue
            val next = candidates.subList(idx + 1, candidates.size).firstOrNull { c -> blockText(c, tocLists) != null }
                ?: continue
            val nextText = next.text().trim()
            if (nextText.isEmpty() || !nextText[0].isLetter()) continue
            val sep = if (startsWithRawWhitespace(next)) " " else ""
            mergedAlt[next] = (mergedAlt[next] ?: "") + el.attr("alt").trim() + sep
            skipImages.add(el)
        }

        if (headingEl == null) {
            val paras = mutableListOf<String>()
            val refs = mutableListOf<ImageRef>()
            for (el in candidates) {
                when {
                    el in skipImages -> Unit
                    isImageTag(el) -> imageSrc(el)?.let { refs.add(ImageRef(it, paras.size)) }
                    else -> blockText(el, tocLists)?.let { paras.add((mergedAlt[el] ?: "") + it) }
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

        // Routes one block's text into preHeading (before the chapter heading, same as today — never
        // checked for epigraph-ness, matching the pre-existing <p> rule) or epigraph/main (after it).
        fun addReadableText(el: Element, text: String) {
            if (text.isBlank()) return
            if (!foundHeading) preHeading.add(text) else if (looksLikeEpigraph(el)) epigraph.add(text) else main.add(text)
        }

        // Whether the lead-in (before the chapter heading) already has at least one real <p> —
        // i.e. whether EpubParser was ALREADY going to turn this file's lead-in into its own
        // standalone chapter before v26. Only then is it safe to read the NEW block types (li, pre,
        // dd/dt, later headings, leaf div/section) in that zone too: they only add MORE paragraphs
        // to a chapter that was going to exist anyway (allowed — paragraph indices inside a chapter
        // may shift). When the lead-in has no <p> at all, none of the new types may be read there
        // either, because EpubParser turns ANY non-empty preHeadingParagraphs into a BRAND-NEW
        // chapter: Gutenberg leaves some front-matter (its own "other editions of this ebook"
        // notice) as an h4 OUTSIDE any of the known boilerplate containers, right before a book's
        // real title heading (alice-illustrated.epub, ebook 28885) — reading that h4 would silently
        // add a chapter and renumber every chapter after it.
        val preHeadingHasP = run {
            for (el in body.select("p, h1, h2, h3")) {
                if (el === headingEl) break
                if (el.closest("blockquote") == null && el.text().trim().isNotBlank()) return@run true
            }
            false
        }

        for (el in body.select("h1, h2, h3, h4, h5, h6, blockquote, p, li, pre, dd, dt, div, section, img, image$rows")) {
            val tag = el.tagName()
            val canReadNewTypes = foundHeading || preHeadingHasP
            when {
                el === headingEl -> foundHeading = true
                el in skipImages -> Unit
                // A table in a blockquote is read with the blockquote (as an epigraph), not twice.
                tag == "tr" -> if (foundHeading && el.closest("blockquote") == null) rowText(el, contentsTables)?.let(main::add)
                (tag == "img" || tag == "image") && foundHeading -> {
                    imageSrc(el)?.let { imageRefs.add(ImageRef(it, main.size, inLegacyLayout = true)) }
                }
                tag == "img" || tag == "image" -> imageSrc(el)?.let { leadingImageRefs.add(ImageRef(it, 0)) }
                tag == "blockquote" && foundHeading -> {
                    val texts = el.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
                        .ifEmpty { listOf(el.text().trim()).filter { it.isNotBlank() } }
                    epigraph.addAll(texts)
                }
                // Blockquote/epigraph handling stays exactly as today: anything else living inside a
                // blockquote (before OR after the heading) is read through the blockquote above, not
                // separately — including a blockquote before the heading, which (as today) reads
                // nothing at all.
                el.closest("blockquote") != null -> Unit
                // A list item is read once, here; anything living INSIDE that item (a nested list's own
                // items, or a <p>/div/pre nested in it) is read separately or not at all — never twice
                // (see blockText's own li-nesting guard, used below for everything but <li> itself).
                tag == "li" && canReadNewTypes -> liText(el, tocLists)?.let { addReadableText(el, (mergedAlt[el] ?: "") + it) }
                el.closest("li") != null -> Unit
                tag == "p" -> addReadableText(el, (mergedAlt[el] ?: "") + el.text().trim())
                (tag == "dd" || tag == "dt" || tag in HEADING_TAGS) && canReadNewTypes ->
                    addReadableText(el, (mergedAlt[el] ?: "") + el.text().trim())
                tag == "pre" && canReadNewTypes -> preText(el).takeIf { it.isNotBlank() }?.let { addReadableText(el, (mergedAlt[el] ?: "") + it) }
                (tag == "div" || tag == "section") && canReadNewTypes ->
                    if (isLeafTextBlock(el)) addReadableText(el, (mergedAlt[el] ?: "") + el.text().trim())
            }
        }

        val finalImageRefs = leadingImageRefs + imageRefs.map { it.copy(anchorParagraph = epigraph.size + it.anchorParagraph) }
        return Result(headingTitle, preHeading, epigraph, main, finalImageRefs)
    }

    /**
     * One block's own readable text, or null if it contributes nothing — shared by the heading-less
     * path's main loop and the drop-cap "next block" lookahead (used by both paths). A block nested
     * inside a `<li>` (other than the `<li>` itself) is never read here: that `<li>`'s own text already
     * covers it (see [liText]), so reading it again here would duplicate it.
     */
    private fun blockText(el: Element, tocLists: Set<Element>): String? {
        val tag = el.tagName()
        if (tag != "li" && el.closest("li") != null) return null
        return when {
            tag == "p" || tag == "dd" || tag == "dt" || tag in HEADING_TAGS -> el.text().trim().takeIf { it.isNotBlank() }
            tag == "div" || tag == "section" -> if (isLeafTextBlock(el)) el.text().trim().takeIf { it.isNotBlank() } else null
            tag == "li" -> liText(el, tocLists)
            tag == "pre" -> preText(el).takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /**
     * Whether this document's text lives in tables: a play set as speaker | line rows (Gutenberg's
     * Czech R.U.R.), verse as line | number rows (the Greek Iliad). Their rows become paragraphs only
     * then — paragraph indices are saved (positions, bookmarks), so a table in an ordinary book
     * must add nothing; a book whose text is in tables showed (next to) nothing before, so its saved
     * positions have nothing to lose.
     */
    private fun readsTables(body: Element, contentsTables: Set<Element>): Boolean {
        // Leaf cells only (Jsoup's select includes the element itself, hence size == 1): a cell
        // holding paragraphs or a nested table is counted through those.
        val inTables = body.select("td, th")
            .filter { it.select("p").isEmpty() && it.select("td, th").size == 1 }
            .filter { it.closest("blockquote") == null && !isLinkCell(it) }
            .filter { cell -> cell.closest("table")?.let { it !in contentsTables } ?: true }
            .sumOf { it.text().length }
        if (inTables == 0) return false
        val inParagraphs = body.select("p").sumOf { it.text().length }
        // Clearly more: a table that merely outweighs the prose (an appendix, a chronology) would
        // put its rows between paragraphs that already read, and shift every saved position after.
        return inTables > TABLE_DOMINANCE * inParagraphs
    }

    /**
     * A table row as one paragraph: its cells joined by " — " (speaker — line), with empty cells and
     * line numbers dropped. Null for a row of nothing but punctuation (a stanza break), a row holding
     * paragraphs of its own (they are read as paragraphs), or one holding a nested table.
     */
    private fun rowText(tr: Element, contentsTables: Set<Element>): String? {
        if (tr.selectFirst("p, table") != null) return null
        // A table of contents or list of plates is never text to read, including its link-less
        // header row ("PAGE", "To face page") — see isContentsTable/findContentsTables.
        tr.closest("table")?.let { if (it in contentsTables) return null }
        val cellEls = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
        // A header row ("Year | Event"), or a contents row of links, is not text to read.
        if (cellEls.isEmpty() || cellEls.all { it.tagName() == "th" } || tr.closest("thead") != null) return null
        if (cellEls.all { it.text().isBlank() || isLinkCell(it) }) return null
        val cells = cellEls.map { it.text().trim() }.filter { it.isNotEmpty() }.toMutableList()
        // A number in the last column is a line or page number; elsewhere (a year) it is content.
        if (cells.size > 1 && cells.last().all { it.isDigit() }) cells.removeAt(cells.lastIndex)
        val text = cells.joinToString(" — ")
        return text.takeIf { t -> t.any { it.isLetterOrDigit() } }
    }

    /** A cell that is nothing but a link: a table-of-contents or plate-list row. */
    private fun isLinkCell(cell: Element): Boolean {
        val text = cell.text().trim()
        return text.isNotEmpty() && cell.select("a").sumOf { it.text().trim().length } >= text.length
    }

    /**
     * A cell that is (almost) nothing but a hyperlink to elsewhere — like [isLinkCell], but counting
     * only `<a href>` elements, never a link-less `<a id>`/`<a name>` anchor. A verse or play table
     * that marks every line with such an anchor (for cross-references, not navigation) must not look
     * like a contents list just because every row "has an `<a>`". Used only by [isContentsTable];
     * [isLinkCell] itself, and the pre-v25 rules in [rowText]/[readsTables] that use it, are
     * unchanged.
     */
    private fun isLinkTargetCell(cell: Element): Boolean {
        val text = cell.text().trim()
        return text.isNotEmpty() && cell.select("a[href]").sumOf { it.text().trim().length } >= text.length
    }

    /**
     * Every table in the document that is a table of contents or a list of plates (see
     * [isContentsTable]), decided ONCE per document — not per row or per cell. [isContentsTable]
     * itself does an O(rows-in-that-table) scan, so calling it from inside [rowText] (once per row)
     * or [readsTables] (once per cell) made parsing quadratic in the row count: Gutenberg's Czech
     * R.U.R. (1,798 `<tr>`) went from 83 ms to 13.2 s, and the Greek Iliad (13,989 `<tr>`) from
     * 145 ms to just under 11 minutes, for identical output. Jsoup's `Node.equals`/`hashCode` are
     * already identity (not structural), so a plain `HashSet` would work too — an explicit
     * identity-based set is used anyway so membership here never depends on Jsoup's `equals`
     * contract at all, matching [isContentsTable]'s own `closest("table") == table` check.
     */
    private fun findContentsTables(body: Element): Set<Element> {
        val result: MutableSet<Element> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
        for (table in body.select("table")) {
            if (isContentsTable(table)) result.add(table)
        }
        return result
    }

    /**
     * A table of contents or a list of plates: nearly every row is a link to a page, so none of it
     * is prose — not even its one link-less header row ("PAGE", "To face page" in Gutenberg's
     * illustrated "Alice", ebook 28885). Judged on this table's OWN rows only (`tr.closest("table")`
     * must be this table): a contents table nested inside an outer layout table — Alice's case — is
     * judged on its own rows, and an outer table wrapping it is unaffected (its own single row holds
     * a nested table, which [rowText] already never reads).
     *
     * Requires at least 2 rows with any text, and at least 80% of them to contain a real link cell
     * ([isLinkTargetCell] — `a[href]` only, so a line/page *anchor* without `href` never counts).
     * 80% tolerates exactly one link-less header row as long as 4 or more linked rows follow it
     * (4 of 5 = 80%); Alice's own contents (12 link rows + 1 header = 12/13 ≈ 92%) and plates
     * (13/14 ≈ 93%) tables clear it with room to spare. A table where only a minority of rows carry
     * a link — an ordinary dialogue/verse table with an occasional footnote reference — stays well
     * under 80% and is read exactly as before. Called once per table by [findContentsTables], never
     * from inside a per-row/per-cell loop.
     */
    private fun isContentsTable(table: Element): Boolean {
        val ownRows = table.select("tr").filter { it.closest("table") == table }
        val textRows = ownRows.filter { it.text().isNotBlank() }
        if (textRows.size < 2) return false
        val linkRows = textRows.count { row ->
            row.children().any { (it.tagName() == "td" || it.tagName() == "th") && isLinkTargetCell(it) }
        }
        return linkRows.toDouble() / textRows.size >= 0.8
    }

    /** How many times the prose a table's text must be before its rows are read (see readsTables). */
    private const val TABLE_DOMINANCE = 4

    /**
     * Every `<ul>`/`<ol>` in the document that is a table of contents or a list of plates — the same
     * spirit as [findContentsTables], decided ONCE per list (not per item, to stay linear: see that
     * function's own perf note). A nested sub-list (a hierarchical contents page) is judged
     * independently on its OWN `<li>` children, so a list of real prose items that merely contains one
     * link-heavy nested list inside one item is unaffected — only that nested list's own items are
     * skipped.
     */
    private fun findTocLists(body: Element): Set<Element> {
        val result: MutableSet<Element> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
        for (list in body.select("ul, ol")) {
            if (isTocList(list)) result.add(list)
        }
        return result
    }

    /**
     * A list where (nearly) every item is a link to elsewhere: a table of contents or list of plates
     * (Don Quixote's `<ol class="toc">`), judged on this list's OWN `<li>` children only — a nested
     * sub-list's items don't count toward (or against) its parent's score, and are stripped out of
     * each item's own text first (see [liOwnText]) so a "Chapter 3" entry that merely happens to have a
     * sub-list of sub-chapters is still judged purely on its own link text. Same 80%/≥2-items threshold
     * as [isContentsTable], for consistency.
     */
    private fun isTocList(list: Element): Boolean {
        val items = list.children().filter { it.tagName() == "li" }
        var textCount = 0
        var linkCount = 0
        for (li in items) {
            val clone = liOwnClone(li)
            val text = clone.text().trim()
            if (text.isEmpty()) continue
            textCount++
            if (clone.select("a[href]").sumOf { it.text().trim().length } >= text.length) linkCount++
        }
        if (textCount < 2) return false
        return linkCount.toDouble() / textCount >= 0.8
    }

    /** [li] with any nested `<ul>`/`<ol>` removed — its own text/links, excluding any nested list's. */
    private fun liOwnClone(li: Element): Element = li.clone().apply { select("ul, ol").remove() }

    /** [li]'s own text, excluding any nested list — the nested list's own items are read separately
     * (see the candidate loops above), never through their parent item too. */
    private fun liOwnText(li: Element): String = liOwnClone(li).text().trim()

    /** [li]'s own readable text, or null if it belongs to a table-of-contents/plate-list [tocLists]. */
    private fun liText(li: Element, tocLists: Set<Element>): String? {
        val list = li.closest("ul, ol") ?: return null
        if (list in tocLists) return null
        return liOwnText(li).takeIf { it.isNotBlank() }
    }

    /**
     * A `<pre>` block's text, keeping its line breaks — Element.text() normalizes (collapses) all
     * whitespace, which would turn Moby-Dick's epigraph quotes, Alice's shaped verse or War & Peace's
     * ASCII diagram into one run-on line. `<br>` becomes a literal newline too. Only the wrapping blank
     * line(s) immediately after `<pre>` and before `</pre>` are trimmed (common in source markup); any
     * interior leading spaces (ASCII-art indentation) are kept exactly as written.
     */
    private fun preText(el: Element): String {
        val sb = StringBuilder()
        fun walk(n: org.jsoup.nodes.Node) {
            when (n) {
                is org.jsoup.nodes.TextNode -> sb.append(n.wholeText)
                is Element -> if (n.tagName() == "br") sb.append("\n") else n.childNodes().forEach(::walk)
                else -> Unit
            }
        }
        el.childNodes().forEach(::walk)
        return sb.toString().trim('\n', '\r')
    }

    /** Resolves the source of an <img> (src) or SVG <image> (any *href attribute), or null if absent. */
    private fun imageSrc(el: Element): String? {
        val raw = el.attr("src").ifBlank {
            el.attributes().firstOrNull { it.key.endsWith("href", ignoreCase = true) }?.value ?: ""
        }
        return raw.trim().takeIf { it.isNotBlank() }
    }

    private fun isImageTag(el: Element): Boolean = el.tagName() == "img" || el.tagName() == "image"

    /**
     * Whether [el]'s first real text, in document order, begins with whitespace in the SOURCE —
     * unlike `Element.text()`, which normalizes (and here, entirely discards) leading whitespace, so
     * a block-level element whose first child is a text node starting with spaces always reports
     * none when asked via `.text()`. Used only to decide whether a merged drop cap needs a separating
     * space before the next block's text (see the drop-cap candidate loop): [TextNode.getWholeText]
     * returns that node's content exactly as parsed, spaces and all.
     */
    private fun startsWithRawWhitespace(el: Element): Boolean {
        fun firstTextNode(node: org.jsoup.nodes.Node): org.jsoup.nodes.TextNode? {
            for (child in node.childNodes()) {
                if (child is org.jsoup.nodes.TextNode) return child
                firstTextNode(child)?.let { return it }
            }
            return null
        }
        return firstTextNode(el)?.wholeText?.firstOrNull()?.isWhitespace() ?: false
    }

    /** A small image that is a letter, not an illustration: a Gutenberg drop cap ("C"), identified by
     * a short alt text or a drop-cap-ish class. Shared by the in-<p> glyph rule and
     * [isStandaloneGlyphContainer] (a drop cap in its own container). */
    private fun looksLikeGlyph(img: Element): Boolean {
        val alt = img.attr("alt").trim()
        return alt.length in 1..3 ||
            img.classNames().any { c -> listOf("drop", "initial", "cap").any { it in c.lowercase() } }
    }

    /**
     * An image whose own container holds nothing else — no other text, no other element — and which
     * is not inside a `<p>` (that case is the pre-existing in-<p> glyph rule, handled separately
     * before candidates are even gathered). Alice's `<div class="figleft"><img alt="A"/></div>` is
     * exactly this: the drop cap is the div's only child, with no sibling text.
     */
    private fun isStandaloneGlyphContainer(img: Element): Boolean {
        if (img.closest("p") != null) return false
        val container = img.parent() ?: return false
        return container.children().size == 1 && container.text().trim().isBlank()
    }

    /** Block-level tags that disqualify a div/section from being read as one stand-alone paragraph —
     * its text is read through that descendant instead (see [isLeafTextBlock]), never twice. */
    private const val BLOCK_DESCENDANT_SELECTOR =
        "p, div, section, table, blockquote, ul, ol, h1, h2, h3, h4, h5, h6, figure, li, pre, dl, dd, dt"

    /**
     * A div/section that is itself one paragraph of real prose: some Gutenberg editions set a
     * chapter's text ("CHAPTER I", a subtitle, the body) in divs rather than `<p>` — in a heading-less
     * file (Gutenberg's illustrated "Alice", ebook 28885) and, just the same, in a file that DOES have
     * a heading (Alice's own chapters 12–13: div.unindent after a drop cap, div.poem). Leaf only:
     * Jsoup's `Element.select` includes the element itself when it matches the selector (as elsewhere
     * in this file), so `el.select(BLOCK_DESCENDANT_SELECTOR).size == 1` means "only itself matched" —
     * no nested p/div/section/table/blockquote/ul/ol/heading/figure/li/pre/dl, so a wrapper div around
     * one of these is skipped in favour of reading through that descendant. Inline markup (`<i>`,
     * `<b>`, `<span>`) inside doesn't disqualify it, hence `.text()`, not `.ownText()`. Also never a
     * div inside a `<p>`, `<li>`, `<blockquote>` or `<pre>` — each of those already reads its own text
     * as a whole (a nested div would otherwise double it).
     *
     * Never a div holding an image, caption or not: `EpubParserImagesTest`'s `<div
     * class="figright"><img/><span class="caption">…</span></div>` must stay exactly as ignored as
     * before — reading its caption as a paragraph would turn a plates-only spine file into a new
     * chapter and renumber every chapter after it.
     */
    private fun isLeafTextBlock(el: Element): Boolean =
        el.closest("p") == null &&
            el.closest("li") == null &&
            el.closest("blockquote") == null &&
            el.closest("pre") == null &&
            el.select(BLOCK_DESCENDANT_SELECTOR).size == 1 &&
            el.select("img, image").isEmpty() &&
            el.text().trim().isNotBlank()

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
