package com.example.splitreader.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPlannerTest {

    // ── foregroundRange ───────────────────────────────────────────────────

    @Test
    fun `foregroundRange from the top spans the visible window`() {
        val range = TranslationPlanner.foregroundRange(anchor = 0, chapterSize = 100)
        assertEquals(0, range.first)
        assertEquals(TranslationPlanner.VISIBLE_WINDOW - 1, range.last)
    }

    @Test
    fun `foregroundRange in the middle is anchored at the scroll position`() {
        val range = TranslationPlanner.foregroundRange(anchor = 40, chapterSize = 100)
        assertEquals(40, range.first)
        assertEquals(40 + TranslationPlanner.VISIBLE_WINDOW - 1, range.last)
    }

    @Test
    fun `foregroundRange near the end clamps to the last paragraph`() {
        val range = TranslationPlanner.foregroundRange(anchor = 98, chapterSize = 100)
        assertEquals(98, range.first)
        assertEquals(99, range.last)
    }

    @Test
    fun `foregroundRange for a chapter shorter than the window covers it whole`() {
        val range = TranslationPlanner.foregroundRange(anchor = 0, chapterSize = 5)
        assertEquals(0, range.first)
        assertEquals(4, range.last)
    }

    @Test
    fun `foregroundRange for an empty chapter is empty`() {
        assertTrue(TranslationPlanner.foregroundRange(anchor = 0, chapterSize = 0).isEmpty())
    }

    // ── backgroundPlan ────────────────────────────────────────────────────

    @Test
    fun `backgroundPlan for ML Kit fills rest of chapter then next chapter then the start`() {
        val plan = TranslationPlanner.backgroundPlan(
            isMlKit = true,
            chapterIndex = 1,
            foregroundEnd = 11,
            anchor = 3,
            chapterSizes = listOf(50, 100, 80),
        )
        assertEquals(3, plan.size)
        // rest of current chapter (after the foreground window)
        assertEquals(TranslationSegment(1, 12, 99, foreground = false), plan[0])
        // next chapter, whole
        assertEquals(TranslationSegment(2, 0, 79, foreground = false), plan[1])
        // start of current chapter (paragraphs above the anchor)
        assertEquals(TranslationSegment(1, 0, 2, foreground = false), plan[2])
    }

    @Test
    fun `backgroundPlan for ML Kit on the last chapter has no next chapter`() {
        val plan = TranslationPlanner.backgroundPlan(
            isMlKit = true,
            chapterIndex = 2,
            foregroundEnd = 11,
            anchor = 0,
            chapterSizes = listOf(50, 100, 80),
        )
        assertEquals(1, plan.size)
        assertEquals(TranslationSegment(2, 12, 79, foreground = false), plan[0])
    }

    @Test
    fun `backgroundPlan for paid providers is empty`() {
        val plan = TranslationPlanner.backgroundPlan(
            isMlKit = false,
            chapterIndex = 1,
            foregroundEnd = 11,
            anchor = 3,
            chapterSizes = listOf(50, 100, 80),
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `backgroundPlan omits rest segment when the window already covers the chapter`() {
        val plan = TranslationPlanner.backgroundPlan(
            isMlKit = true,
            chapterIndex = 0,
            foregroundEnd = 4,
            anchor = 0,
            chapterSizes = listOf(5, 100),
        )
        // current chapter fully covered; only the next chapter remains
        assertEquals(1, plan.size)
        assertEquals(TranslationSegment(1, 0, 99, foreground = false), plan[0])
    }

    // ── needsPrefetch ─────────────────────────────────────────────────────

    @Test
    fun `needsPrefetch triggers within the trigger distance of the edge`() {
        val edge = 30
        val scrollPos = edge - TranslationPlanner.PREFETCH_TRIGGER
        assertTrue(TranslationPlanner.needsPrefetch(edge, scrollPos, chapterSize = 100))
    }

    @Test
    fun `needsPrefetch does not trigger when far from the edge`() {
        assertFalse(TranslationPlanner.needsPrefetch(edge = 30, scrollPos = 0, chapterSize = 100))
    }

    @Test
    fun `needsPrefetch does not trigger at the end of the chapter`() {
        assertFalse(TranslationPlanner.needsPrefetch(edge = 99, scrollPos = 99, chapterSize = 100))
    }

    // ── prefetchRange ─────────────────────────────────────────────────────

    @Test
    fun `prefetchRange extends lookahead ahead of scroll`() {
        val range = TranslationPlanner.prefetchRange(edge = 30, scrollPos = 28, chapterSize = 100)
        assertEquals(31, range!!.first)
        assertEquals(28 + TranslationPlanner.LOOKAHEAD, range.last)
    }

    @Test
    fun `prefetchRange returns null when nothing new is ahead`() {
        assertNull(TranslationPlanner.prefetchRange(edge = 99, scrollPos = 99, chapterSize = 100))
    }

    // ── progressPercent ───────────────────────────────────────────────────

    @Test
    fun `progressPercent is monotonic 0 to 100 across a segment`() {
        val total = 8
        var last = -1
        for (done in 0..total) {
            val p = TranslationPlanner.progressPercent(done, total)
            assertTrue("progress should not decrease", p >= last)
            last = p
        }
        assertEquals(100, last)
    }

    @Test
    fun `progressPercent of an empty segment is zero`() {
        assertEquals(0, TranslationPlanner.progressPercent(done = 0, total = 0))
    }

    // ── shouldTranslate ───────────────────────────────────────────────────

    @Test
    fun `shouldTranslate keeps ML Kit running even when the pane is hidden`() {
        assertTrue(TranslationPlanner.shouldTranslate(isMlKit = true, translationVisible = false))
        assertTrue(TranslationPlanner.shouldTranslate(isMlKit = true, translationVisible = true))
    }

    @Test
    fun `shouldTranslate skips paid providers when the pane is hidden`() {
        assertFalse(TranslationPlanner.shouldTranslate(isMlKit = false, translationVisible = false))
    }

    @Test
    fun `shouldTranslate runs paid providers when the pane is visible`() {
        assertTrue(TranslationPlanner.shouldTranslate(isMlKit = false, translationVisible = true))
    }
}
