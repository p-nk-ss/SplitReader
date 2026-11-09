package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Guards the reader's "trim the beginning" behaviour: Project Gutenberg source/license boilerplate
 * is stripped, but the actual opening of the book must never be lost or over-trimmed. Pure JVM —
 * [HtmlChapterExtractor] runs on Jsoup with no Android dependency.
 */
class HtmlChapterExtractorTest {

    private fun allParagraphs(r: HtmlChapterExtractor.Result): List<String> =
        r.preHeadingParagraphs + r.epigraphParagraphs + r.mainParagraphs

    // ── Concern #1/#2: Gutenberg boilerplate removed, real opening kept ────────

    @Test
    fun `strips Project Gutenberg boilerplate but keeps the first real paragraph`() {
        val html = """
            <body>
              <section id="pg-header">
                <p>The Project Gutenberg eBook of A Test Book</p>
                <p>This ebook is for the use of anyone anywhere in the United States...</p>
                <div id="pg-machine-header">Title: A Test Book</div>
              </section>
              <div class="pg-boilerplate pgheader">More licensing notes here.</div>
              <h1>Chapter I</h1>
              <p>It was the best of times, it was the worst of times.</p>
              <p>The second real paragraph of the story.</p>
              <section id="pg-footer">
                <p>END OF THE PROJECT GUTENBERG EBOOK</p>
                <div id="project-gutenberg-license">Full license text...</div>
              </section>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals("Chapter I", r.headingTitle)
        assertEquals(
            listOf(
                "It was the best of times, it was the worst of times.",
                "The second real paragraph of the story.",
            ),
            r.mainParagraphs,
        )
        // The opening of the book is the FIRST main paragraph — not lost, not shifted.
        assertEquals("It was the best of times, it was the worst of times.", r.mainParagraphs.first())
        // No boilerplate leaked into any bucket.
        val joined = allParagraphs(r).joinToString("\n")
        assertFalse(joined.contains("Project Gutenberg"))
        assertFalse(joined.contains("for the use of anyone"))
        assertFalse(joined.contains("license", ignoreCase = true))
    }

    // ── No-op on non-Gutenberg content ────────────────────────────────────────

    @Test
    fun `leaves non-Gutenberg html untouched`() {
        val html = """
            <body>
              <h2>Глава 1</h2>
              <p>Первый абзац книги.</p>
              <p>Второй абзац книги.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals("Глава 1", r.headingTitle)
        assertEquals(listOf("Первый абзац книги.", "Второй абзац книги."), r.mainParagraphs)
        assertEquals("Первый абзац книги.", r.mainParagraphs.first())
    }

    // ── Anti-over-trim: trimming is structural (by container), not by text ─────

    @Test
    fun `keeps real content that merely mentions Project Gutenberg in its text`() {
        // A paragraph that *names* Project Gutenberg but is NOT inside a pg-* container must survive:
        // over-eager text-based stripping would wrongly delete the book's opening.
        val html = """
            <body>
              <h1>Preface</h1>
              <p>This edition was produced from Project Gutenberg sources, yet it is real prose.</p>
              <p>The story proper begins right here.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(2, r.mainParagraphs.size)
        assertTrue(r.mainParagraphs.first().contains("Project Gutenberg sources"))
    }

    @Test
    fun `keeps content in pg-like classes that are not the known boilerplate containers`() {
        // div.pgwide / id="pgepubid00001" are real-content wrappers in some PG epubs; the selectors
        // only target the specific boilerplate ids/classes, so this content must remain.
        val html = """
            <body>
              <h1>Chapter One</h1>
              <div class="pgwide"><p>A wide table caption that is genuine content.</p></div>
              <p id="pgepubid00001">An anchored opening paragraph.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        val joined = allParagraphs(r).joinToString("\n")
        assertTrue(joined.contains("genuine content"))
        assertTrue(joined.contains("An anchored opening paragraph."))
    }

    // ── Headless lead-in (content before the first heading) is preserved ──────

    @Test
    fun `preserves content that appears before the first heading after stripping boilerplate`() {
        val html = """
            <body>
              <section id="pg-header"><p>Boilerplate to remove.</p></section>
              <p>A headless lead-in paragraph that opens the book before any heading.</p>
              <h1>Chapter One</h1>
              <p>Body text follows.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertTrue(
            "pre-heading opening must be retained",
            r.preHeadingParagraphs.any { it.contains("headless lead-in") },
        )
        assertFalse(allParagraphs(r).joinToString("\n").contains("Boilerplate"))
    }

    // ── Edge: empty boilerplate containers are a clean no-op ──────────────────

    @Test
    fun `empty Gutenberg containers do not affect real content`() {
        val html = """
            <body>
              <div id="pg-header"></div>
              <div id="pg-start-separator">   </div>
              <h1>T</h1>
              <p>The only real paragraph.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals("T", r.headingTitle)
        assertEquals(listOf("The only real paragraph."), r.mainParagraphs)
    }

    // ── Text set in tables (2026-11-04: Czech R.U.R. opened empty, the Greek Iliad showed only notes) ──

    @Test
    fun `a play set in a table reads as one paragraph per line, speaker first`() {
        val html = """
            <body><h2>PŘEDEHRA</h2><p>Ústřední kancelář továrny.</p>
            <table>
              <tr><td>Domin</td><td>(slavnostně) A tehdy, slečno, starý Rossum napsal mezi své chemické vzorce tohleto.</td></tr>
              <tr><td>Helena</td><td>Tak dál.</td></tr>
              <tr><td></td><td></td></tr>
              <tr><td>Domin</td><td>Dál? Teď šlo o to, dostat život ze zkumavky ven a zrychlit vývoj.</td></tr>
            </table></body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf(
                "Ústřední kancelář továrny.",
                "Domin — (slavnostně) A tehdy, slečno, starý Rossum napsal mezi své chemické vzorce tohleto.",
                "Helena — Tak dál.",
                "Domin — Dál? Teď šlo o to, dostat život ze zkumavky ven a zrychlit vývoj.",
            ),
            allParagraphs(r),
        )
    }

    @Test
    fun `verse set in a table drops its line numbers and stanza dots`() {
        val html = """
            <body><h2>Α</h2>
            <table>
              <tr><td>Μούσα, τραγούδα το θυμό του ξακουστού Αχιλέα,</td><td></td></tr>
              <tr><td>κι' όλα τα όρνια (του Διός έτσι είχε η γνώμη ορίσει),</td><td>5</td></tr>
              <tr><td>.</td><td></td></tr>
              <tr><td>Πιος τάχα λες τους έβαλε θεός να λογοφέρουν;</td><td></td></tr>
            </table></body>
        """.trimIndent()

        assertEquals(
            listOf(
                "Μούσα, τραγούδα το θυμό του ξακουστού Αχιλέα,",
                "κι' όλα τα όρνια (του Διός έτσι είχε η γνώμη ορίσει),",
                "Πιος τάχα λες τους έβαλε θεός να λογοφέρουν;",
            ),
            allParagraphs(HtmlChapterExtractor.parse(html)),
        )
    }

    @Test
    fun `a small table in a book of paragraphs adds nothing, so saved positions keep their paragraphs`() {
        val html = """
            <body><h1>Chapter I</h1>
            <p>A long first paragraph of the story, written in the ordinary way, with plenty of words in it.</p>
            <table><tr><td>Table 1</td><td>12</td></tr></table>
            <p>A second paragraph of the story, also long enough to outweigh the little table above it.</p>
            </body>
        """.trimIndent()

        assertEquals(2, HtmlChapterExtractor.parse(html).mainParagraphs.size)
    }

    @Test
    fun `a table without a heading, or before it, adds nothing, so no chapter appears or moves`() {
        // A headless table file (a contents page) was dropped as empty; read, it would become a new
        // chapter and renumber every chapter after it. Rows before a heading would open one.
        val headless = """<body><table><tr><td>Domin</td><td>Slečno Gloryová, to byl ohromný okamžik.</td></tr></table></body>"""
        assertTrue(HtmlChapterExtractor.parse(headless).isEmpty)
        val before = """<body><table><tr><td>Domin</td><td>Slečno Gloryová, to byl ohromný okamžik.</td></tr></table>
            <h2>PŘEDEHRA</h2><table><tr><td>Helena</td><td>Tak dál.</td></tr></table></body>"""
        val r = HtmlChapterExtractor.parse(before)
        assertEquals(emptyList<String>(), r.preHeadingParagraphs)
        assertEquals(listOf("Helena — Tak dál."), r.mainParagraphs)
    }

    // ── Review (v22): only books whose text really is in tables change ──

    @Test
    fun `a contents table bigger than the prose around it adds nothing`() {
        val rows = (1..30).joinToString("") { "<tr><td><a href=\"#ch$it\">Chapter $it. The adventure of the number $it</a></td><td><a href=\"#ch$it\">$it</a></td></tr>" }
        val html = """<body><h1>The Book</h1><p>Contents</p><table>$rows</table>
            <p>Transcriber's note: spelling has been kept as in the original.</p></body>"""
        assertEquals(2, HtmlChapterExtractor.parse(html).mainParagraphs.size)
    }

    @Test
    fun `a table that only outweighs the prose a little adds nothing`() {
        val rows = (1..6).joinToString("") { "<tr><td>Year $it of the reign</td><td>The harvest failed again and the granaries stood empty</td></tr>" }
        val prose = "<p>" + "A long paragraph of ordinary prose about the reign and its troubles. ".repeat(3) + "</p>"
        val html = "<body><h1>Appendix</h1>$prose<table>$rows</table>$prose</body>"
        assertEquals(2, HtmlChapterExtractor.parse(html).mainParagraphs.size)
    }

    @Test
    fun `a table inside a blockquote is not read twice`() {
        val html = """<body><h2>I</h2><blockquote><table>
            <tr><td>Arma virumque cano, Troiae qui primus ab oris</td></tr>
            <tr><td>Italiam fato profugus Laviniaque venit litora</td></tr></table></blockquote></body>"""
        val all = allParagraphs(HtmlChapterExtractor.parse(html))
        assertEquals("$all", 1, all.count { "Arma virumque" in it })
    }

    @Test
    fun `a year in the first column is kept, a line number in the last is dropped, a header row is skipped`() {
        val html = """<body><h2>Chronology</h2><table>
            <tr><th>Year</th><th>Event</th></tr>
            <tr><td>1066</td><td>Battle of Hastings, and the end of Anglo-Saxon rule in England</td></tr>
            <tr><td>Domesday Book compiled for the whole of the conquered kingdom</td><td>5</td></tr>
            </table></body>"""
        assertEquals(
            listOf(
                "1066 — Battle of Hastings, and the end of Anglo-Saxon rule in England",
                "Domesday Book compiled for the whole of the conquered kingdom",
            ),
            allParagraphs(HtmlChapterExtractor.parse(html)),
        )
    }

    // ── Tables of contents / lists of plates are not read as text (v25: Gutenberg's illustrated
    // "Alice", ebook 28885 — "PAGE", "To face page" and every contents/plates line were translated) ──

    @Test
    fun `a contents table nested inside an outer layout table, and a list-of-plates table, are not read as paragraphs, prose survives`() {
        // Mirrors Alice's real front matter exactly: the contents table is nested inside an outer
        // layout table (one row: a spine image in one cell, the real contents table in the other) —
        // the structure that makes isContentsTable's "own rows only" rule (tr.closest("table") ==
        // this table) matter: judging the OUTER table's rows would see only 1 own row (holding the
        // nested table) and never reach 80%; the INNER table must be judged on its own 13 rows.
        //
        // This must fail on main 7f00ed6's logic (pre-v25, no isContentsTable at all) for the same
        // reason the non-nested case does: rowText there only skips a row when ALL its cells are
        // blank or "nothing but a link" (isLinkCell); the header row ("PAGE"/"To face page") has one
        // non-blank, non-link cell, so it reads as a paragraph, and every contents/plates data row
        // reads as "<numeral> — <title>" once its trailing page-link cell is dropped as a line
        // number. Nesting the table inside an outer one changes nothing about that: the outer row
        // holding the nested table is already skipped by the pre-existing `tr.selectFirst("p, table")
        // != null` check in rowText, so the bug is exactly the inner table's rows, nested or not.
        val contentsRows = (1..12).joinToString("") {
            "<tr><td>$it.</td><td>Chapter $it of the story</td><td><a href=\"#p$it\">$it</a></td></tr>"
        }
        val platesRows = (1..13).joinToString("") {
            "<tr><td>Plate caption number $it describing a picture</td><td><a href=\"#pl$it\">$it</a></td></tr>"
        }
        val html = """
            <body>
              <h1>The Book</h1>
              <p>A short preface paragraph with a little real prose in it, which must survive untouched.</p>
              <h2>CONTENTS</h2>
              <table><tbody><tr><td><img src="spine.jpg"/></td><td>
                <table><tbody>
                  <tr><td> </td><td> </td><td><small>PAGE</small></td></tr>
                  $contentsRows
                </tbody></table>
              </td></tr></tbody></table>
              <h2>LIST OF THE PLATES</h2>
              <table><tbody>
                <tr><td> </td><td><i>To face page</i></td></tr>
                $platesRows
              </tbody></table>
            </body>
        """.trimIndent()

        val all = allParagraphs(HtmlChapterExtractor.parse(html))

        assertTrue("$all", all.none { it.contains("PAGE") })
        assertTrue("$all", all.none { it.contains("To face page") })
        assertTrue("$all", all.none { it.contains("Chapter") && it.contains("of the story") })
        assertTrue("$all", all.none { it.contains("Plate caption") })
        assertTrue("$all", all.any { it.contains("preface paragraph") })
    }

    @Test
    fun `the same contents and plates tables with no surrounding prose still read nothing from them`() {
        val contentsRows = (1..12).joinToString("") {
            "<tr><td>$it.</td><td>Chapter $it of the story</td><td><a href=\"#p$it\">$it</a></td></tr>"
        }
        val platesRows = (1..13).joinToString("") {
            "<tr><td>Plate caption number $it describing a picture</td><td><a href=\"#pl$it\">$it</a></td></tr>"
        }
        val html = """
            <body>
              <h1>The Book</h1>
              <h2>CONTENTS</h2>
              <table><tbody>
                <tr><td> </td><td> </td><td><small>PAGE</small></td></tr>
                $contentsRows
              </tbody></table>
              <h2>LIST OF THE PLATES</h2>
              <table><tbody>
                <tr><td> </td><td><i>To face page</i></td></tr>
                $platesRows
              </tbody></table>
            </body>
        """.trimIndent()

        val all = allParagraphs(HtmlChapterExtractor.parse(html))

        assertTrue("$all", all.none { it.contains("PAGE") })
        assertTrue("$all", all.none { it.contains("To face page") })
        assertTrue("$all", all.none { it.contains("Chapter") && it.contains("of the story") })
        assertTrue("$all", all.none { it.contains("Plate caption") })
    }

    @Test
    fun `a play table with one footnote link in ten rows still reads every row`() {
        // A minority link (a footnote reference) must not make an ordinary dialogue table look like
        // a contents list: isLinkCell requires (nearly) the whole cell to be link text, which a cell
        // of real dialogue plus a small footnote anchor never satisfies.
        val rows = (1..10).joinToString("") {
            val footnote = if (it == 5) """<sup><a href="#fn1">1</a></sup>""" else ""
            "<tr><td>Speaker $it</td><td>Line of dialogue number $it spoken on stage$footnote</td></tr>"
        }
        val html = "<body><h2>Act I</h2><table>$rows</table></body>"

        val all = allParagraphs(HtmlChapterExtractor.parse(html))

        assertEquals(10, all.size)
    }

    @Test
    fun `uppercase text is never treated as a table header or contents row, nor is an all-caps paragraph dropped`() {
        val html = """
            <body><h2>PŘEDEHRA</h2>
            <p>THE END.</p>
            <table>
              <tr><td>Domin</td><td>(slavnostně) A tehdy, slečno, starý Rossum napsal mezi své chemické vzorce tohleto.</td></tr>
              <tr><td>HELENA</td><td>WHAT DOES THIS MEAN? I DEMAND AN ANSWER AT ONCE.</td></tr>
            </table></body>
        """.trimIndent()

        val all = allParagraphs(HtmlChapterExtractor.parse(html))

        assertTrue("$all", all.any { it == "THE END." })
        assertTrue("$all", all.any { it == "HELENA — WHAT DOES THIS MEAN? I DEMAND AN ANSWER AT ONCE." })
    }

    // ── Review round 2 (v25): decide contents tables once per document, not once per row/cell ──

    @Test
    fun `a play table where every row's line number is an anchor without href still reads every row`() {
        // Some books wrap a line number in <a id="lineN"> purely for cross-referencing — no href, so
        // it is not a hyperlink to another page. isContentsTable must use isLinkTargetCell (a[href]
        // only), not isLinkCell (any <a>): isLinkCell would see the whole last cell's text as "link"
        // text (the anchor has visible text "N") in every one of these 10 rows, score 100%, and wrongly
        // classify this ordinary dialogue table as a table of contents — dropping all 10 rows.
        val rows = (1..10).joinToString("") {
            "<tr><td>Speaker $it</td><td>Line of dialogue number $it spoken on stage</td><td><a id=\"line$it\">$it</a></td></tr>"
        }
        val html = "<body><h2>Act I</h2><table>$rows</table></body>"

        val all = allParagraphs(HtmlChapterExtractor.parse(html))

        assertEquals("$all", 10, all.size)
    }

    @Test
    fun `a table of several thousand rows parses well within a time budget`() {
        // isContentsTable does an O(rows-in-that-table) scan; calling it from inside rowText (once
        // per row) or readsTables (once per cell) — as the first v25 commit did — made parsing
        // quadratic in the row count. Measured on the real fixtures: Gutenberg's Czech R.U.R. (1,798
        // <tr>) went from 83 ms to 13.2 s, and the Greek Iliad (13,989 <tr>) from 145 ms to ~11
        // minutes, for identical output. Contents tables must be decided ONCE per document
        // (findContentsTables) so this stays linear: 4,000 rows well under a generous 3 s budget.
        val rows = (1..4000).joinToString("") {
            "<tr><td>Speaker $it</td><td>Line of dialogue number $it spoken on stage during the performance</td></tr>"
        }
        val html = "<body><h2>Act I</h2><table>$rows</table></body>"

        var size = -1
        val elapsedMs = measureTimeMillis { size = HtmlChapterExtractor.parse(html).mainParagraphs.size }

        assertEquals(4000, size)
        assertTrue("parsing 4000 table rows took ${elapsedMs}ms, expected well under 3000ms", elapsedMs < 3000)
    }
}
