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

    // ── v26: heading-less chapters that set text in divs, not <p> (Gutenberg's illustrated "Alice",
    // ebook 28885, file …-h-1.htm.xhtml — the opening paragraph and "CHAPTER I" / "Down the
    // Rabbit-Hole" were dropped entirely, and the drop-cap "A" showed as a stray picture) ──

    @Test
    fun `a heading-less chapter files its text divs as paragraphs, and merges a standalone drop cap into the next block`() {
        // Mirrors the real file's opening exactly: a title div, a subtitle div (text wrapped in
        // <i>), a div holding ONLY the drop-cap image (no other content), then the div that
        // continues the sentence the drop cap started, then an ordinary <p>.
        val html = """
            <body><div class="chapter">CHAPTER I</div>
            <div class="sidenote"><i>Down the Rabbit-Hole</i></div>
            <div class="figleft" style="width: 71px;"><img alt="A" src="images/a.png"/></div><div class="unindent">LICE was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, "and what is the use of a book," thought Alice, "without pictures or conversations?"</div>
            <p>So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf(
                "CHAPTER I",
                "Down the Rabbit-Hole",
                "ALICE was beginning to get very tired of sitting by her sister on the bank, and of having " +
                    "nothing to do: once or twice she had peeped into the book her sister was reading, but it " +
                    "had no pictures or conversations in it, \"and what is the use of a book,\" thought Alice, " +
                    "\"without pictures or conversations?\"",
                "So she was considering in her own mind (as well as she could, for the hot day made her feel " +
                    "very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the " +
                    "trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes " +
                    "ran close by her.",
            ),
            r.mainParagraphs,
        )
        // The drop cap was merged into the text, not emitted as a picture.
        assertTrue("${r.imageRefs}", r.imageRefs.isEmpty())
    }

    @Test
    fun `a div holding only an image, plus a caption, is never read as a paragraph`() {
        // isLeafTextBlock must exclude any div with an image descendant, caption or not — otherwise
        // a plates-only spine file (EpubParserImagesTest's figright/figleft divs: image + a <span
        // class="caption">) would gain a "paragraph" it never had and become a new chapter, which
        // would renumber every chapter after it.
        val html = """
            <body><p>Real paragraph one, long enough to be unambiguous prose.</p>
            <div class="figright"><img src="a.png"/><span class="caption">Caption text here</span></div>
            <p>Real paragraph two, also long enough to be unambiguous prose.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf("Real paragraph one, long enough to be unambiguous prose.", "Real paragraph two, also long enough to be unambiguous prose."),
            r.mainParagraphs,
        )
        assertEquals(listOf(1), r.imageRefs.map { it.anchorParagraph })
    }

    @Test
    fun `an image-only heading-less file is still image-only, not a new chapter`() {
        val html = """<body><div class="figright"><img src="a.png"/><span class="caption">Caption</span></div></body>"""

        val r = HtmlChapterExtractor.parse(html)

        assertTrue("$r", r.isImageOnly)
    }

    @Test
    fun `a heading-less chapter of only paragraphs is unchanged`() {
        val html = """
            <body><p>First paragraph of a heading-less chapter.</p>
            <p>Second paragraph continues the text.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf("First paragraph of a heading-less chapter.", "Second paragraph continues the text."),
            r.mainParagraphs,
        )
    }

    @Test
    fun `a drop cap that is itself a whole word keeps the space before the next word`() {
        // Alice's real Chapter VIII: the drop cap "A" is the complete word "A" (not the start of a
        // longer word like "ALICE"), and the source has three literal spaces before "LARGE" —
        // Element.text() normalizes (discards) that leading whitespace entirely, so naively
        // concatenating alt + next.text() would wrongly produce "ALARGE".
        val html = """
            <body><div class="chapter">CHAPTER VIII</div>
            <div class="figleft" style="width: 71px;"><img alt="A" src="images/a.png"/></div><div class="unindent">   LARGE rose-tree stood near the entrance of the garden.</div>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf("CHAPTER VIII", "A LARGE rose-tree stood near the entrance of the garden."),
            r.mainParagraphs,
        )
        assertTrue("$r", r.imageRefs.isEmpty())
    }

    // ── v26: every visible text block is read — later headings, lists, pre, divs with a heading
    // (audit of 67 real books: 450 visible text blocks silently dropped in 21 books) ──

    @Test
    fun `every h1 to h6 after the first heading becomes an ordinary paragraph, in order`() {
        val html = """
            <body><h1>Don Quixote</h1>
            <p>An introductory paragraph.</p>
            <h2>Capítulo I</h2>
            <p>En un lugar de la Mancha.</p>
            <h4>A deep aeolus headline</h4>
            <p>Text after the headline.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals("Don Quixote", r.headingTitle)
        assertEquals(
            listOf(
                "An introductory paragraph.",
                "Capítulo I",
                "En un lugar de la Mancha.",
                "A deep aeolus headline",
                "Text after the headline.",
            ),
            r.mainParagraphs,
        )
    }

    @Test
    fun `an h4 to h6 anywhere in a heading-less file is read as a paragraph too`() {
        val html = """
            <body><h5>A subheading</h5>
            <p>Body text.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(listOf("A subheading", "Body text."), r.mainParagraphs)
    }

    @Test
    fun `an h4 to h6 before the chapter heading is NOT read when the lead-in has no p at all`() {
        // EpubParser turns any non-empty preHeadingParagraphs into a brand-new chapter, so when the
        // lead-in (content before the chapter heading) has no <p> at all -- meaning EpubParser was
        // NOT already going to create a chapter for it -- the new block types must not create one
        // either. Gutenberg's own "other editions of this ebook" notice is an h4 OUTSIDE any of the
        // known boilerplate containers, right before a real book's title heading
        // (alice-illustrated.epub, ebook 28885) -- reading it here would silently add a chapter and
        // renumber every chapter after it.
        val html = """
            <body><h5>Not a real chapter heading, must not create one</h5>
            <h1>Chapter I</h1>
            <p>Body text.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(emptyList<String>(), r.preHeadingParagraphs)
        assertEquals("Chapter I", r.headingTitle)
        assertEquals(listOf("Body text."), r.mainParagraphs)
    }

    @Test
    fun `an h4 to h6 before the chapter heading IS read once the lead-in already has a p`() {
        // When the lead-in already has a real <p> (anywhere in it, even AFTER this h5), EpubParser
        // was already going to turn it into its own chapter -- so reading the h5 too only adds
        // another paragraph to a chapter that exists either way (allowed: paragraph indices inside
        // a chapter may shift). Mirrors a real case: Gutenberg's illustrated "Alice" repeats a
        // chapter's closing lines (set in divs) before the NEXT chapter's own heading in the same
        // file, alongside <p>-based lead-in text.
        val html = """
            <body><h5>A real subheading in the lead-in</h5>
            <p>Lead-in text.</p>
            <h1>Chapter I</h1>
            <p>Body text.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(listOf("A real subheading in the lead-in", "Lead-in text."), r.preHeadingParagraphs)
        assertEquals("Chapter I", r.headingTitle)
        assertEquals(listOf("Body text."), r.mainParagraphs)
    }

    @Test
    fun `ordinary list items are read as paragraphs, in document order`() {
        val html = """
            <body><h1>Ulysses</h1>
            <ul>
              <li>First plain list item with real prose in it.</li>
              <li>Second plain list item, also real prose.</li>
            </ul>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf("First plain list item with real prose in it.", "Second plain list item, also real prose."),
            r.mainParagraphs,
        )
    }

    @Test
    fun `a list where nearly every item is a link to elsewhere is a table of contents and is skipped whole`() {
        val rows = (1..12).joinToString("") { "<li><a href=\"#ch$it\">Chapter $it</a></li>" }
        val html = """<body><h1>The Book</h1><p>Preface prose that must survive.</p><ol class="toc">$rows</ol></body>"""

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(listOf("Preface prose that must survive."), r.mainParagraphs)
    }

    @Test
    fun `a nested list reads the parent item's own text once, and its nested items separately`() {
        val html = """
            <body><h1>Chapter</h1>
            <ul>
              <li>Outer item own text.
                <ul><li>Inner item one.</li><li>Inner item two.</li></ul>
              </li>
            </ul>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(listOf("Outer item own text.", "Inner item one.", "Inner item two."), r.mainParagraphs)
    }

    @Test
    fun `a div or section after the chapter heading is read as a paragraph too`() {
        val html = """
            <body><h1>Alice</h1>
            <div class="unindent">A paragraph set in a div after the heading, not a p tag.</div>
            <p>An ordinary paragraph that follows it.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(
            listOf("A paragraph set in a div after the heading, not a p tag.", "An ordinary paragraph that follows it."),
            r.mainParagraphs,
        )
    }

    @Test
    fun `a pre block is read as one paragraph, keeping its line breaks`() {
        // Not a triple-quoted/trimIndent literal: trimIndent would strip the ASCII-art-style leading
        // spaces on the <pre> content's second line along with the markup's own indentation.
        val html = "<body><h1>Moby-Dick</h1><pre>   Call me Ishmael.\n   Some years ago--never mind how long precisely--</pre></body>"

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(1, r.mainParagraphs.size)
        assertTrue(r.mainParagraphs[0].contains("\n"))
        assertEquals(
            "   Call me Ishmael.\n   Some years ago--never mind how long precisely--",
            r.mainParagraphs[0],
        )
    }

    @Test
    fun `a standalone drop cap is merged into the next block even in a file that has a heading`() {
        val html = """
            <body><h1>Chapter VIII</h1>
            <div class="figleft" style="width: 71px;"><img alt="A" src="images/a.png"/></div><p>LICE wondered what would happen next.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(listOf("ALICE wondered what would happen next."), r.mainParagraphs)
        assertTrue("${r.imageRefs}", r.imageRefs.isEmpty())
    }

    @Test
    fun `a list item, pre, or div before the chapter heading is not read either, same as h4-h6`() {
        val html = """
            <body><ul><li>A list item before any heading, must not become a chapter.</li></ul>
            <pre>A pre block before any heading, must not become a chapter.</pre>
            <div>A leaf div before any heading, must not become a chapter.</div>
            <h1>Chapter I</h1>
            <p>Body text.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(emptyList<String>(), r.preHeadingParagraphs)
        assertEquals("Chapter I", r.headingTitle)
        assertEquals(listOf("Body text."), r.mainParagraphs)
    }

    @Test
    fun `nothing is read twice -- a paragraph inside a list item, and a div inside a list item`() {
        val html = """
            <body><h1>Chapter</h1>
            <ul>
              <li><p>A paragraph living inside a list item.</p></li>
              <li><div>A div living inside a list item.</div></li>
            </ul>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(1, r.mainParagraphs.count { it.contains("A paragraph living inside a list item.") })
        assertEquals(1, r.mainParagraphs.count { it.contains("A div living inside a list item.") })
    }

    // ── v26 fix round: the chapter structure is exactly v25's (12e9c6f) for every file. Whether a
    // file yields a chapter (heading-less), an image-only page, or a lead-in chapter is decided by
    // whether v25 found text there -- never by the new block types. ──

    private fun assertNoChapter(html: String) {
        val r = HtmlChapterExtractor.parse(html)
        assertTrue("v25 read nothing here, so no chapter may appear: $r", r.isEmpty)
    }

    private fun assertImageOnly(html: String, images: List<String>) {
        val r = HtmlChapterExtractor.parse(html)
        assertTrue("v25 saw an image page here, not a chapter: $r", r.isImageOnly)
        assertEquals(images, r.imageRefs.map { it.src })
    }

    @Test
    fun `a heading-less file whose only text is a short div, h4, list, pre or span-wrapped div gains no chapter`() {
        assertNoChapter("<body><div class='part'>Part One</div></body>")
        assertNoChapter("<body><h4>Part One</h4></body>")
        assertNoChapter("<body><ul><li>To my mother</li><li>To my father</li></ul></body>")
        assertNoChapter("<body><pre>  *  *  *</pre></body>")
        assertNoChapter("<body><div><span>This is a long dedication set inside a span in a div</span></div></body>")
    }

    @Test
    fun `a MOBI title-page fragment set in font tags inside a div gains no chapter`() {
        assertNoChapter("<body><div align='center'><font size='+2'>The Title Of The Book</font></div></body>")
    }

    @Test
    fun `a heading-less picture page with a caption div stays an image page`() {
        assertImageOnly("<body><div><img src='a.jpg' alt='Frontispiece'/></div><div class='caption'>She went on.</div></body>", listOf("a.jpg"))
        assertImageOnly("<body><img src='a.jpg'/><div><span>This is a long caption of a picture page here</span></div></body>", listOf("a.jpg"))
    }

    @Test
    fun `a drop cap followed by a short div in a heading-less file stays an image page with its image`() {
        assertImageOnly("<body><div class='figleft'><img src='a.png' alt='A'/></div><div>LICE was beginning</div></body>", listOf("a.png"))
    }

    @Test
    fun `a file whose heading has no text, followed by only a short div, stays an image page`() {
        assertImageOnly("<body><h1><img src='t.png'/></h1><div>Part One</div></body>", listOf("t.png"))
    }

    @Test
    fun `a heading-less contents list of paragraphs in list items still yields its chapter`() {
        val r = HtmlChapterExtractor.parse(
            "<body><ol><li><p><a href='c1.html'>Chapter 1</a></p></li><li><p><a href='c2.html'>Chapter 2</a></p></li></ol></body>",
        )
        assertEquals(listOf("Chapter 1", "Chapter 2"), r.mainParagraphs)
    }

    @Test
    fun `a contents list of paragraphs before the heading still yields the lead-in chapter`() {
        val r = HtmlChapterExtractor.parse(
            "<body><ol><li><p><a href='c1.html'>One</a></p></li><li><p><a href='c2.html'>Two</a></p></li></ol><h2>Chapter</h2><p>Text.</p></body>",
        )
        assertEquals(listOf("One", "Two"), r.preHeadingParagraphs)
        assertEquals("Chapter", r.headingTitle)
        assertEquals(listOf("Text."), r.mainParagraphs)
    }

    // ── Verse in divs stays where it is (Dracula's "Denn die Todten reiten schnell") ──

    @Test
    fun `verse set in poem and stanza divs stays in document order, not moved to the chapter start`() {
        val html = """
            <body><h2>CHAPTER I</h2>
            <p class="epigraph">An epigraph paragraph keeps going first.</p>
            <p>First paragraph of the chapter.</p>
            <div class="poem"><div class="stanza"><span class="i0">Denn die Todten reiten schnell.<br/></span></div></div>
            <p>The paragraph after the verse.</p>
            </body>
        """.trimIndent()

        val r = HtmlChapterExtractor.parse(html)

        assertEquals(listOf("An epigraph paragraph keeps going first."), r.epigraphParagraphs)
        assertEquals(
            listOf("First paragraph of the chapter.", "Denn die Todten reiten schnell.", "The paragraph after the verse."),
            r.mainParagraphs,
        )
    }

    // ── Each text is read once ──

    private fun assertReadOnce(html: String, vararg texts: String) {
        val all = allParagraphs(HtmlChapterExtractor.parse(html))
        for (t in texts) assertEquals("'$t' in $all", 1, all.count { it.contains(t) })
    }

    @Test
    fun `a paragraph inside a dd is read once`() {
        assertReadOnce("<body><h2>H</h2><dl><dt>Term</dt><dd><p>Definition text that is long enough</p></dd></dl></body>", "Term", "Definition text")
    }

    @Test
    fun `nested dl verse reads each line once`() {
        assertReadOnce(
            "<body><h2>H</h2><dl><dd>Outer line of verse here<dl><dd>Inner indented line of verse</dd></dl></dd></dl></body>",
            "Outer line of verse here", "Inner indented line of verse",
        )
    }

    @Test
    fun `a div inside a table cell of a play read as rows is read once`() {
        assertReadOnce(
            "<body><h2>H</h2><table><tr><td><div>Speaker</div></td><td>A long line spoken by the speaker in the play</td></tr></table></body>",
            "Speaker", "A long line spoken",
        )
    }

    @Test
    fun `a table inside a list item is read once`() {
        assertReadOnce(
            "<body><h2>H</h2><ul><li><table><tr><td>Alpha cell text long enough</td><td>Beta</td></tr></table></li></ul></body>",
            "Alpha cell text",
        )
    }

    @Test
    fun `a paragraph inside a pre is read once`() {
        assertReadOnce("<body><h2>H</h2><pre><p>Para inside pre</p></pre></body>", "Para inside pre")
    }

    // ── A drop cap merges only into text that is actually read ──

    @Test
    fun `an ornament initial before the chapter heading keeps its image and is not merged across the heading`() {
        val r = HtmlChapterExtractor.parse("<body><div><img src='orn.png' alt='I'/></div><h2>Chapter I</h2><p>Text.</p></body>")

        assertEquals("Chapter I", r.headingTitle)
        assertEquals(emptyList<String>(), r.preHeadingParagraphs)
        assertEquals(listOf("Text."), r.mainParagraphs)
        assertEquals(listOf("orn.png"), r.imageRefs.map { it.src })
    }
}
