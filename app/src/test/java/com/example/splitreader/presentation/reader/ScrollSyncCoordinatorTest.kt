package com.example.splitreader.presentation.reader

import com.example.splitreader.presentation.reader.ScrollSyncCoordinator.Pane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSyncCoordinatorTest {

    @Test
    fun `first pane to physically scroll becomes the leader`() {
        val c = ScrollSyncCoordinator()
        assertTrue(c.onScrollStateChanged(Pane.TOP, scrolling = true))
        assertEquals(Pane.TOP, c.leader())
        assertEquals(Pane.BOTTOM, c.follower())
    }

    @Test
    fun `the second pane does not steal leadership while the first still scrolls`() {
        val c = ScrollSyncCoordinator()
        c.onScrollStateChanged(Pane.TOP, scrolling = true)
        assertFalse(c.onScrollStateChanged(Pane.BOTTOM, scrolling = true))
        assertEquals(Pane.TOP, c.leader())
    }

    @Test
    fun `leadership is released on settle`() {
        val c = ScrollSyncCoordinator()
        c.onScrollStateChanged(Pane.TOP, scrolling = true)
        c.onScrollStateChanged(Pane.TOP, scrolling = false)
        assertNull(c.leader())
    }

    @Test
    fun `either pane may lead — the bottom one can too`() {
        val c = ScrollSyncCoordinator()
        assertTrue(c.onScrollStateChanged(Pane.BOTTOM, scrolling = true))
        assertEquals(Pane.TOP, c.follower())
    }

    /**
     * The whole reason masking exists: `scrollToItem` raises `isScrollInProgress` on the pane the
     * coordinator is itself driving. Without the mask the follower would claim leadership from
     * the motion it was just given, and the two panes would drive each other in a loop.
     *
     * The dangerous instant is when nobody currently leads (`leader == null`) — e.g. the leader
     * has just settled while the masked follower's own programmatic scroll is still catching up.
     * That is the only instant at which `if (leader == null) leader = pane` would otherwise fire,
     * so the assertion here must catch it with no other pane already holding leadership; a scenario
     * that starts with another pane already leading can pass even with the guard deleted, because
     * the null-check alone already blocks the reassignment.
     */
    @Test
    fun `a masked pane never claims leadership`() {
        val c = ScrollSyncCoordinator()
        c.beginProgrammaticScroll(Pane.BOTTOM)

        assertFalse(c.onScrollStateChanged(Pane.BOTTOM, scrolling = true))
        assertNull(c.leader())

        // Also true once someone else already leads — masking must not be mistaken for a claim.
        c.endProgrammaticScroll(Pane.BOTTOM)
        c.onScrollStateChanged(Pane.TOP, scrolling = true)
        c.beginProgrammaticScroll(Pane.BOTTOM)
        assertFalse(c.onScrollStateChanged(Pane.BOTTOM, scrolling = true))
        assertEquals(Pane.TOP, c.leader())
    }

    @Test
    fun `unmasking restores the pane's ability to lead`() {
        val c = ScrollSyncCoordinator()
        c.beginProgrammaticScroll(Pane.BOTTOM)
        assertFalse(c.onScrollStateChanged(Pane.BOTTOM, scrolling = true))
        assertNull(c.leader())

        c.endProgrammaticScroll(Pane.BOTTOM)

        assertTrue(c.onScrollStateChanged(Pane.BOTTOM, scrolling = true))
    }

    @Test
    fun `a masked leader still loses leadership when it settles`() {
        val c = ScrollSyncCoordinator()
        c.onScrollStateChanged(Pane.TOP, scrolling = true)
        c.beginProgrammaticScroll(Pane.TOP)
        c.onScrollStateChanged(Pane.TOP, scrolling = false)
        assertNull(c.leader())
    }

    // ── computeFollowerTarget ────────────────────────────────────────────

    @Test
    fun `follower keeps the leader's index and scales the offset by its own item height`() {
        val t = computeFollowerTarget(
            leaderFirstIndex = 7, leaderOffsetPx = 50, leaderItemH = 100, followerItemH = 200,
        )
        assertEquals(7, t.index)
        assertEquals(100, t.offsetPx)
    }

    @Test
    fun `an unknown follower height falls back to the leader's`() {
        val t = computeFollowerTarget(
            leaderFirstIndex = 3, leaderOffsetPx = 50, leaderItemH = 100, followerItemH = null,
        )
        assertEquals(50, t.offsetPx)
    }

    /** 0.95 keeps the follower inside the item; 1.0 would land it on the next one. */
    @Test
    fun `the fraction is clamped to 0_95`() {
        val t = computeFollowerTarget(
            leaderFirstIndex = 1, leaderOffsetPx = 100, leaderItemH = 100, followerItemH = 100,
        )
        assertEquals(95, t.offsetPx)
    }

    @Test
    fun `a degenerate leader height yields the item top rather than dividing by zero`() {
        val t = computeFollowerTarget(
            leaderFirstIndex = 4, leaderOffsetPx = 30, leaderItemH = 0, followerItemH = 120,
        )
        assertEquals(4, t.index)
        assertEquals(0, t.offsetPx)
    }
}
