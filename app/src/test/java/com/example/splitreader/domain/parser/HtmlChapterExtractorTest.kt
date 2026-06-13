package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
