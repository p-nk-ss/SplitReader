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
}
