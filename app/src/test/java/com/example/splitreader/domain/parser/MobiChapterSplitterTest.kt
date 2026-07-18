package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiChapterSplitterTest {

    @Test fun pageBreaks_splitIntoFragments() {
        val html = "<p>a</p><mbp:pagebreak/><p>b</p><mbp:pagebreak/><p>c</p>"
        assertEquals(3, MobiChapterSplitter.split(html).size)
    }

    @Test fun noBreaks_splitsOnHeadings() {
        val html = "<h2>Ch1</h2><p>a</p><h2>Ch2</h2><p>b</p><h2>Ch3</h2><p>c</p>"
        val frags = MobiChapterSplitter.split(html)
        assertEquals(3, frags.size)
        assertTrue(frags.all { it.startsWith("<h2") })
    }

    @Test fun noBreaksNoHeadings_singleFragment() {
        val html = "<p>a</p><p>b</p><p>c</p>"
        assertEquals(1, MobiChapterSplitter.split(html).size)
    }

    @Test fun pageBreakBefore_splits() {
        val html = "<p>a</p><div style=\"page-break-before:always\">b</div>"
        assertEquals(2, MobiChapterSplitter.split(html).size)
    }

    @Test fun shallowestHeadingLevelWins() {
        val html = "<h1>Part 1</h1><h2>Sub</h2><p>a</p><h1>Part 2</h1><h2>Sub</h2><p>b</p>"
        val frags = MobiChapterSplitter.split(html)
        assertEquals(2, frags.size) // split on h1 only, not h2
        assertTrue(frags.all { it.startsWith("<h1") })
    }

    @Test fun pageBreaks_takePrecedenceOverHeadings() {
        val html = "<h2>A</h2><p>a</p><mbp:pagebreak/><h2>B</h2><p>b</p>"
        // 2 page-break fragments -> trust breaks, do NOT additionally split on headings
        assertEquals(2, MobiChapterSplitter.split(html).size)
    }

    @Test fun preHeadingContentKeptAsFirstFragment() {
        val html = "<p>front matter</p><h2>Ch1</h2><p>a</p>"
        val frags = MobiChapterSplitter.split(html)
        assertEquals(2, frags.size)
        assertTrue(frags[0].contains("front matter"))
        assertTrue(frags[1].startsWith("<h2"))
    }
}
