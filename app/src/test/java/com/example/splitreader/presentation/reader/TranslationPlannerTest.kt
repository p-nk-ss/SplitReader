package com.example.splitreader.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPlannerTest {

    /** Predicate: nothing is translated yet. */
    private val none: (Int, Int) -> Boolean = { _, _ -> false }

    // ── windowPlan: within a single chapter ───────────────────────────────

    @Test
    fun `windowPlan covers visible span then lookahead then lookbehind`() {
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 0, visibleStartPara = 10,
            visibleEndChapter = 0, visibleEndPara = 14,
            chapterSizes = listOf(50),
            isTranslated = none,
        )
        assertEquals(
            listOf(
                TranslationSegment(0, 10, 14, foreground = true),                                  // visible
                TranslationSegment(0, 15, 14 + TranslationPlanner.LOOKAHEAD, foreground = false),  // lookahead
                TranslationSegment(0, 10 - TranslationPlanner.LOOKBEHIND, 9, foreground = false),   // lookbehind
            ),
            plan,
        )
    }

    @Test
    fun `windowPlan marks only the visible segment as foreground`() {
        val plan = TranslationPlanner.windowPlan(0, 10, 0, 14, listOf(50), none)
        assertEquals(1, plan.count { it.foreground })
        assertTrue(plan.first().foreground)
        assertTrue(plan.drop(1).none { it.foreground })
    }

    // ── windowPlan: spanning several short chapters (guards skipped-short-chapters bug) ──

    @Test
    fun `windowPlan covers every chapter in a multi-chapter visible span`() {
        // sizes: ch0=5 (g0..4), ch1=3 (g5..7), ch2=4 (g8..11), ch3=6 (g12..17)
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 1, visibleStartPara = 0,
            visibleEndChapter = 2, visibleEndPara = 3,
            chapterSizes = listOf(5, 3, 4, 6),
            isTranslated = none,
        )
        // Both visible short chapters are fully covered as foreground.
        assertEquals(TranslationSegment(1, 0, 2, foreground = true), plan[0])
        assertEquals(TranslationSegment(2, 0, 3, foreground = true), plan[1])
        // Lookahead spills into the next chapter; lookbehind into the previous one.
        assertEquals(TranslationSegment(3, 0, 5, foreground = false), plan[2]) // g12..17 (8 ahead clamped)
        assertEquals(TranslationSegment(0, 1, 4, foreground = false), plan[3]) // g1..4 (4 behind)
        assertEquals(4, plan.size)
    }

    // ── windowPlan: lookbehind reaches the previous chapter (guards deep-open scroll-up bug) ──

    @Test
    fun `windowPlan lookbehind crosses into the previous chapter when opened deep`() {
        // Opened at the very top of chapter 1; nothing before it has been translated.
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 1, visibleStartPara = 0,
            visibleEndChapter = 1, visibleEndPara = 0,
            chapterSizes = listOf(20, 20),
            isTranslated = none,
        )
        assertEquals(TranslationSegment(1, 0, 0, foreground = true), plan[0])
        assertEquals(TranslationSegment(1, 1, TranslationPlanner.LOOKAHEAD, foreground = false), plan[1])
        // The tail of the *previous* chapter is translated without any large scroll-up.
        assertEquals(TranslationSegment(0, 20 - TranslationPlanner.LOOKBEHIND, 19, foreground = false), plan[2])
    }

    // ── windowPlan: clamping at book bounds ───────────────────────────────

    @Test
    fun `windowPlan at the book start has no lookbehind`() {
        val plan = TranslationPlanner.windowPlan(0, 0, 0, 0, listOf(10), none)
        assertEquals(TranslationSegment(0, 0, 0, foreground = true), plan[0])
        assertEquals(TranslationSegment(0, 1, TranslationPlanner.LOOKAHEAD, foreground = false), plan[1])
        assertEquals(2, plan.size)
    }

    @Test
    fun `windowPlan at the book end has no lookahead and clamps lookbehind`() {
        val plan = TranslationPlanner.windowPlan(0, 9, 0, 9, listOf(10), none)
        assertEquals(TranslationSegment(0, 9, 9, foreground = true), plan[0])
        assertEquals(TranslationSegment(0, 9 - TranslationPlanner.LOOKBEHIND, 8, foreground = false), plan[1])
        assertEquals(2, plan.size)
    }

    @Test
    fun `windowPlan for an empty book is empty`() {
        assertTrue(TranslationPlanner.windowPlan(0, 0, 0, 0, emptyList(), none).isEmpty())
        assertTrue(TranslationPlanner.windowPlan(0, 0, 0, 0, listOf(0, 0), none).isEmpty())
    }

    // ── windowPlan: already-translated paragraphs are pruned ──────────────

    @Test
    fun `windowPlan splits the visible span around already-translated paragraphs`() {
        val translated: (Int, Int) -> Boolean = { ch, p -> ch == 0 && (p == 1 || p == 2) }
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 0, visibleStartPara = 0,
            visibleEndChapter = 0, visibleEndPara = 4,
            chapterSizes = listOf(10),
            isTranslated = translated,
        )
        // Paragraphs 1 and 2 are done, so the visible span splits into [0] and [3,4].
        assertEquals(TranslationSegment(0, 0, 0, foreground = true), plan[0])
        assertEquals(TranslationSegment(0, 3, 4, foreground = true), plan[1])
        assertTrue(plan.none { it.chapterIndex == 0 && it.start <= 2 && it.endInclusive >= 1 })
    }

    @Test
    fun `windowPlan omits a band entirely when every paragraph in it is translated`() {
        // Whole chapter already translated: visible + lookahead/behind all prune to nothing.
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 0, visibleStartPara = 2,
            visibleEndChapter = 0, visibleEndPara = 4,
            chapterSizes = listOf(10),
            isTranslated = { _, _ -> true },
        )
        assertTrue(plan.isEmpty())
    }

    // ── windowPlan: jumping several chapters forward retains earlier chapters ──

    @Test
    fun `jumping forward several chapters does not re-plan already-translated earlier chapters`() {
        // 6 chapters of 10 paragraphs each. Chapters 0..4 are already fully translated (the reader
        // read/translated them, then jumped ahead). Landing at the top of chapter 5 must NOT schedule
        // any work in chapters 0..4 — their translations stay cached and on screen.
        val earlierDone: (Int, Int) -> Boolean = { ch, _ -> ch < 5 }
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 5, visibleStartPara = 0,
            visibleEndChapter = 5, visibleEndPara = 1,
            chapterSizes = listOf(10, 10, 10, 10, 10, 10),
            isTranslated = earlierDone,
        )
        // No segment touches a previous chapter (lookbehind into ch4 is pruned away as already done).
        assertTrue(
            "previous chapters must not be re-translated when jumping forward",
            plan.none { it.chapterIndex < 5 },
        )
        // Only the newly-visible chapter 5 is planned (visible span + clamped lookahead).
        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.chapterIndex == 5 })
    }

    @Test
    fun `lookbehind only reaches the immediately preceding untranslated paragraphs`() {
        // Jump to the middle of chapter 3; chapters 0..2 already done, chapter 3 fresh. Lookbehind is
        // bounded to LOOKBEHIND paragraphs, so it never sweeps back across whole earlier chapters.
        val earlierDone: (Int, Int) -> Boolean = { ch, _ -> ch < 3 }
        val plan = TranslationPlanner.windowPlan(
            visibleStartChapter = 3, visibleStartPara = 5,
            visibleEndChapter = 3, visibleEndPara = 6,
            chapterSizes = listOf(10, 10, 10, 10),
            isTranslated = earlierDone,
        )
        assertTrue("must not reach back into earlier chapters", plan.none { it.chapterIndex < 3 })
        // Lookbehind within chapter 3: paragraphs 1..4 (5 - LOOKBEHIND .. 4).
        assertTrue(plan.any { it == TranslationSegment(3, 5 - TranslationPlanner.LOOKBEHIND, 4, foreground = false) })
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
