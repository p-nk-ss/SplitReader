package com.example.splitreader.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class DividerDragMathTest {

    @Test
    fun `drag right increases ratio proportionally to pane width`() {
        // 100px right on a 1000px pane = +0.1
        assertEquals(0.6f, DividerDragMath.newRatio(0.5f, 100f, 1000f), 1e-6f)
    }

    @Test
    fun `drag left decreases ratio`() {
        assertEquals(0.4f, DividerDragMath.newRatio(0.5f, -100f, 1000f), 1e-6f)
    }

    @Test
    fun `ratio clamps at upper bound`() {
        assertEquals(0.7f, DividerDragMath.newRatio(0.65f, 500f, 1000f), 1e-6f)
    }

    @Test
    fun `ratio clamps at lower bound`() {
        assertEquals(0.3f, DividerDragMath.newRatio(0.35f, -500f, 1000f), 1e-6f)
    }

    @Test
    fun `zero pane width returns current ratio unchanged`() {
        assertEquals(0.5f, DividerDragMath.newRatio(0.5f, 100f, 0f), 1e-6f)
    }

    @Test
    fun `negative pane width returns current ratio unchanged`() {
        assertEquals(0.5f, DividerDragMath.newRatio(0.5f, 100f, -50f), 1e-6f)
    }

    @Test
    fun `out-of-range current value is clamped even with zero delta`() {
        assertEquals(0.7f, DividerDragMath.newRatio(0.9f, 0f, 1000f), 1e-6f)
    }

    // ── Stacked (vertical) divider ───────────────────────────────────────

    @Test
    fun `drag down grows the top pane proportionally to pane height`() {
        // 200px down on a 1000px-tall area = +0.2
        assertEquals(0.7f, DividerDragMath.newVerticalRatio(0.5f, 200f, 1000f), 1e-6f)
    }

    @Test
    fun `drag up shrinks the top pane`() {
        assertEquals(0.35f, DividerDragMath.newVerticalRatio(0.5f, -150f, 1000f), 1e-6f)
    }

    @Test
    fun `vertical ratio clamps at upper bound`() {
        assertEquals(0.7f, DividerDragMath.newVerticalRatio(0.65f, 500f, 1000f), 1e-6f)
    }

    @Test
    fun `vertical ratio clamps at lower bound`() {
        assertEquals(0.3f, DividerDragMath.newVerticalRatio(0.35f, -500f, 1000f), 1e-6f)
    }

    /** Guards the first frame, where the pane area has not been measured yet. */
    @Test
    fun `zero pane height returns current ratio unchanged`() {
        assertEquals(0.5f, DividerDragMath.newVerticalRatio(0.5f, 200f, 0f), 1e-6f)
    }

    @Test
    fun `negative pane height returns current ratio unchanged`() {
        assertEquals(0.5f, DividerDragMath.newVerticalRatio(0.5f, 200f, -50f), 1e-6f)
    }

    /**
     * Bounds as literals, deliberately not as `VERTICAL_SPLIT_RATIO_RANGE.start/.endInclusive`:
     * asserting a value against the same constant the implementation reads is an identity that
     * moves with any edit and therefore cannot fail. With literals, changing the vertical range
     * fails here and the author has to mean it.
     *
     * KNOWN LIMIT, stated rather than implied: `SPLIT_RATIO_RANGE` and
     * `VERTICAL_SPLIT_RATIO_RANGE` currently hold the *same* numbers (0.3..0.7). While that is
     * true, **no behavioural test can tell which of the two constants `newVerticalRatio` reads** —
     * wiring it to the horizontal range would leave every test here green. The separation is
     * enforced by the code being two entry points reading two named constants, not by this test.
     * If the ranges ever diverge, add an assertion that pins them apart.
     */
    @Test
    fun `vertical ratio is clamped to its own bounds`() {
        assertEquals(0.7f, DividerDragMath.newVerticalRatio(0.5f, 100_000f, 1000f), 1e-6f)
        assertEquals(0.3f, DividerDragMath.newVerticalRatio(0.5f, -100_000f, 1000f), 1e-6f)
    }
}
