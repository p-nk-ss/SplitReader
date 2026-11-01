package com.example.splitreader.presentation.reader

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Found on a tablet: in the side-by-side layout the divider grip, drawn mid-height over the list,
 * crossed illustrations and chapter titles scrolling under it. It hides over full-width rows.
 */
class DividerGripTest {

    private fun row(key: String, offset: Int, size: Int) = object : LazyListItemInfo {
        override val index = 0
        override val key: Any = key
        override val offset = offset
        override val size = size
        override val contentType: Any? = null
    }

    // The grip band: viewport pixels 476..524.
    private val top = 476
    private val bottom = 524

    @Test
    fun `an illustration across the grip hides it`() {
        assertTrue(isFullWidthItemUnder(listOf(row("img_2_0", 300, 400)), top, bottom))
    }

    @Test
    fun `a chapter title across the grip hides it`() {
        assertTrue(isFullWidthItemUnder(listOf(row("masthead_3", 450, 60)), top, bottom))
    }

    @Test
    fun `a paragraph across the grip leaves it, the grip sits in the gutter between the panes`() {
        assertFalse(isFullWidthItemUnder(listOf(row("p_2_5", 300, 400)), top, bottom))
    }

    @Test
    fun `an illustration above or below the grip leaves it`() {
        val rows = listOf(row("img_2_0", 0, top), row("img_2_1", bottom, 300), row("p_2_4", top, bottom - top))
        assertFalse(isFullWidthItemUnder(rows, top, bottom))
    }

    @Test
    fun `an image still decoding, or undecodable, is 0 px and hides nothing`() {
        assertFalse(isFullWidthItemUnder(listOf(row("img_0_0", 500, 0)), top, bottom))
    }

    @Test
    fun `the keys bookItems gives full-width rows`() {
        assertTrue(isFullWidthItem("masthead_0"))
        assertTrue(isFullWidthItem("img_0_1"))
        assertFalse(isFullWidthItem("p_0_1"))
        assertFalse(isFullWidthItem("end_padding"))
    }
}
