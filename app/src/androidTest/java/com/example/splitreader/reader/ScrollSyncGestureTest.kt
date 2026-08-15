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
     * The one a JVM test cannot express: after the gesture settles, nothing keeps moving.
     *
     * An unmasked feedback loop shows up here — the follower's induced motion claims leadership and
     * drives the leader back, so the panes oscillate instead of coming to rest. `awaitSettled`
     * requires two agreeing samples a frame apart, so a loop that never converges fails the wait.
     */
    @Test
    fun afterFlingBothPanesComeToRestAndStayThere() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        topPane.performTouchInput { swipeUp() }
        val first = awaitSettled(top, bottom)

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
