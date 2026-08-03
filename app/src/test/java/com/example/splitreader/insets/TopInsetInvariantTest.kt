package com.example.splitreader.insets

import com.example.splitreader.presentation.navigation.READER_ROUTE
import com.example.splitreader.presentation.navigation.ShellTestTags
import com.example.splitreader.screenshot.PHONE_LANDSCAPE
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * I1: content never starts above the status-bar inset, in any shell arm.
 *
 * This is the defect that shipped first in Phase 2b: AppStatusStrip was the app's only consumer of
 * the top inset, so the reader's first line of text ran under the system clock. The reader
 * variants matter most — there the strip is replaced by a bare inset spacer, and that spacer is
 * exactly what a future "simplification" would delete as useless.
 */
class TopInsetInvariantTest : ShellInsetTest() {

    private val statusBarPx = 63

    private fun assertContentClearsStatusBar() {
        val top = boundsOf(ShellTestTags.CONTENT).top
        assertTrue(
            "Content starts at $top, above the ${statusBarPx}px status-bar inset — " +
                "something on this path stopped consuming the top inset.",
            top >= statusBarPx,
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact arm consumes the top inset`() {
        composeShell(shellInsets(statusBars = statusBarPx))
        assertContentClearsStatusBar()
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact reader arm consumes the top inset`() {
        composeShell(shellInsets(statusBars = statusBarPx), currentRoute = READER_ROUTE)
        assertContentClearsStatusBar()
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail arm consumes the top inset`() {
        composeShell(shellInsets(statusBars = statusBarPx))
        assertContentClearsStatusBar()
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail reader arm consumes the top inset`() {
        composeShell(shellInsets(statusBars = statusBarPx), currentRoute = READER_ROUTE)
        assertContentClearsStatusBar()
    }

    @Test
    fun `full rail arm consumes the top inset`() {
        composeShell(shellInsets(statusBars = statusBarPx))
        assertContentClearsStatusBar()
    }

    @Test
    fun `full rail reader arm consumes the top inset`() {
        composeShell(shellInsets(statusBars = statusBarPx), currentRoute = READER_ROUTE)
        assertContentClearsStatusBar()
    }
}
