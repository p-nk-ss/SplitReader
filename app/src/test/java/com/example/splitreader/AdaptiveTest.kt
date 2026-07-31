package com.example.splitreader

import androidx.compose.ui.unit.dp
import com.example.splitreader.presentation.theme.COMPACT_WIDTH_THRESHOLD
import com.example.splitreader.presentation.theme.isCompactWidth
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
}
