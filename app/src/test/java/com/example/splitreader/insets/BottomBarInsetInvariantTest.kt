package com.example.splitreader.insets

import com.example.splitreader.presentation.navigation.ShellTestTags
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * I4: the bottom bar spans to the bottom edge, and its content sits above the gesture inset.
 *
 * Not a defect that has shipped — the mirror of one that did, pinned before it can.
 */
class BottomBarInsetInvariantTest : ShellInsetTest() {

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `bottom bar reaches the bottom edge and keeps its content above the gesture inset`() {
        val navPx = 48
        composeShell(shellInsets(statusBars = 63, navigationBars = navPx))

        val bar = boundsOf(ShellTestTags.BOTTOM_BAR)
        val content = boundsOf(ShellTestTags.CONTENT)

        assertTrue(
            "The bar's bottom (${bar.bottom}) must sit below its content area's bottom " +
                "(${content.bottom}) — the bar is what separates content from the gesture area.",
            bar.bottom > content.bottom,
        )
        assertTrue(
            "The bar is ${bar.height} tall but must be at least the 64dp content row plus the " +
                "${navPx}px gesture inset — its background is not reaching the edge.",
            bar.height > navPx,
        )
    }
}
