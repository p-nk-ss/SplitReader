package com.example.splitreader

import androidx.compose.ui.unit.dp
import com.example.splitreader.presentation.theme.COMPACT_WIDTH_THRESHOLD
import com.example.splitreader.presentation.theme.RAIL_MIN_HEIGHT
import com.example.splitreader.presentation.theme.isCompactWidth
import com.example.splitreader.presentation.theme.isRailTooTall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {

    @Test
    fun `threshold is 600dp`() {
        assertEquals(600.dp, COMPACT_WIDTH_THRESHOLD)
    }

    @Test
    fun `width just below the threshold is compact`() {
        assertTrue(isCompactWidth(599.dp))
    }

    @Test
    fun `width exactly at the threshold is not compact`() {
        assertFalse(isCompactWidth(600.dp))
    }

    @Test
    fun `width above the threshold is not compact`() {
        assertFalse(isCompactWidth(601.dp))
    }

    @Test
    fun `phone widths are compact`() {
        assertTrue(isCompactWidth(360.dp))
        assertTrue(isCompactWidth(411.dp))
    }

    @Test
    fun `tablet width is not compact`() {
        assertFalse(isCompactWidth(1280.dp))
    }

    @Test
    fun `degenerate zero width is compact`() {
        assertTrue(isCompactWidth(0.dp))
    }

    @Test
    fun `rail min height is 530dp`() {
        assertEquals(530.dp, RAIL_MIN_HEIGHT)
    }

    @Test
    fun `height just below the rail minimum is too tall for the rail`() {
        assertTrue(isRailTooTall(529.dp))
    }

    @Test
    fun `height exactly at the rail minimum fits the rail`() {
        assertFalse(isRailTooTall(530.dp))
    }

    @Test
    fun `height above the rail minimum fits the rail`() {
        assertFalse(isRailTooTall(531.dp))
    }

    @Test
    fun `tablet landscape gets the rail`() {
        assertFalse(isCompactWidth(1280.dp))
        assertFalse(isRailTooTall(800.dp))
    }

    @Test
    fun `tablet portrait gets the rail`() {
        assertFalse(isCompactWidth(800.dp))
        assertFalse(isRailTooTall(1280.dp))
    }

    @Test
    fun `phone portrait gets the bottom bar on width`() {
        assertTrue(isCompactWidth(411.dp))
        assertFalse(isRailTooTall(891.dp))
    }

    @Test
    fun `phone landscape is too short for the full rail`() {
        assertFalse(isCompactWidth(891.dp))
        assertTrue(isRailTooTall(411.dp))
    }

    @Test
    fun `tablet split-screen half gets the rail`() {
        assertFalse(isCompactWidth(640.dp))
        assertFalse(isRailTooTall(800.dp))
    }
}
