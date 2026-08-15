package com.example.splitreader.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The invariant no JVM test could reach: under a **real** gesture the panes stay together and the
 * leader is not dragged back by the follower it just moved.
 *
 * A JVM test issues one `scrollToItem` and waits; a finger issues a stream of positions and then a
 * fling that keeps moving after it lifts. That difference is the whole reason this file exists.
 */
@RunWith(AndroidJUnit4::class)
class ScrollSyncGestureTest : ReaderGestureTest() {

    @Test
    fun realDragOnTopPaneBringsBottomPaneWithIt() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        topPane.performTouchInput { swipeUp() }
        val settled = awaitSettled(top, bottom)

        assertTrue(
            "The gesture did not move the top pane at all (still at item ${settled.topIndex}) — " +
                "the swipe missed the pane or the list did not accept it.",
            settled.topIndex > 0,
        )
        assertEquals(
            "After a real drag the top pane is at item ${settled.topIndex} but the bottom pane is " +
                "at ${settled.bottomIndex} — the panes are not synchronised under gesture input.",
            settled.topIndex, settled.bottomIndex,
        )
    }

    @Test
    fun realDragOnBottomPaneBringsTopPaneWithIt() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        bottomPane.performTouchInput { swipeUp() }
        val settled = awaitSettled(top, bottom)

        assertTrue(settled.bottomIndex > 0)
        assertEquals(settled.bottomIndex, settled.topIndex)
    }

    /**
     * The one a JVM test cannot express: after the gesture settles, nothing keeps driving the
     * panes. `awaitSettled` requires two agreeing samples a frame apart, so any re-trigger that
     * never converges — from any cause, not just the mask — fails the wait.
     *
     * This is NOT a demonstrated mask test: Task 3's mask-removal experiment ran this exact test
     * with `beginProgrammaticScroll`/`endProgrammaticScroll` removed from `scrollFollower`, and it
     * stayed green with identical settled indices to the masked run. The measured verdict is that
     * the mask is inert under every gesture this suite produces (see
     * `.superpowers/sdd/2026-08-09-phase3-instrumented-testing/task-3-report.md`) — this test
     * exists to catch a runaway re-trigger loop regardless of what would cause one, not to prove
     * the mask prevents it.
     */
    @Test
    fun afterFlingBothPanesComeToRestAndStayThere() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        topPane.performTouchInput { swipeUp() }
        val first = awaitSettled(top, bottom)

        assertTrue(
            "The gesture did not move the top pane at all (still at item ${first.topIndex}) — the " +
                "swipe missed the pane or the list did not accept it, which would make the " +
                "settled-and-stays-settled assertion below pass vacuously at rest.",
            first.topIndex > 0,
        )

        // Sample again well after settling: a slow oscillation would show up as drift here even if
        // the two samples above happened to agree. Real elapsed time, not `mainClock` — see
        // `awaitSettled`'s comment for why the clock cannot be driven by hand here.
        Thread.sleep(500L)
        composeRule.waitForIdle()
        val second = awaitSettled(top, bottom)

        assertEquals(
            "The panes moved after settling: top ${first.topIndex}/${first.topOffset} -> " +
                "${second.topIndex}/${second.topOffset}, bottom ${first.bottomIndex}/" +
                "${first.bottomOffset} -> ${second.bottomIndex}/${second.bottomOffset}. " +
                "Something is still driving them — a feedback loop between the panes.",
            first, second,
        )
    }
}
