package com.example.splitreader.insets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.splitreader.presentation.navigation.READER_ROUTE
import com.example.splitreader.presentation.navigation.ShellTestTags
import com.example.splitreader.presentation.theme.Spacing
import com.example.splitreader.screenshot.PHONE_LANDSCAPE
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import com.example.splitreader.screenshot.TABLET
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
 *
 * The floor is derived **per arm**, not shared. A single `top >= statusBarPx` floor is insensitive
 * in the arms that put a fixed-height status strip above the content: the strip alone is taller
 * than the inset (44dp = 115.5px at 420dpi vs a 63px inset), so deleting the strip's
 * `windowInsetsPadding` still leaves `top` above the floor and the test stays green with the defect
 * back. Each arm therefore asserts against the chrome it actually stacks above CONTENT.
 */
class TopInsetInvariantTest : ShellInsetTest() {

    private val statusBarPx = 63

    /** The same [Spacing] instance `SplitReaderTheme` provides, so the strip heights cannot drift. */
    private val spacing = Spacing()

    /**
     * Asserts CONTENT starts below the top inset *plus* this arm's own chrome.
     *
     * @param strip the fixed-height chrome `AppShell` stacks above CONTENT in this arm — the status
     *   strip's height — or `0.dp` for the arms whose top slot is the bare inset `Spacer`, whose
     *   whole height IS the inset.
     */
    private fun assertContentClearsStatusBar(strip: Dp = 0.dp) {
        val top = boundsOf(ShellTestTags.CONTENT).top
        val stripPx = with(composeRule.density) { strip.toPx() }
        val expected = statusBarPx + stripPx
        assertTrue(
            "Content starts at ${top}px. This arm stacks the ${statusBarPx}px top inset plus " +
                "${strip.value}dp of status strip (${stripPx}px) above it, so it must start at or below " +
                "${expected}px — something on this path stopped consuming the top inset.",
            top >= expected - 1f,
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact arm consumes the top inset`() {
        composeShell(insets(statusBars = statusBarPx))
        assertContentClearsStatusBar(strip = spacing.statusBarCompact)
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact reader arm consumes the top inset`() {
        composeShell(insets(statusBars = statusBarPx), currentRoute = READER_ROUTE)
        assertContentClearsStatusBar()
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail arm consumes the top inset`() {
        composeShell(insets(statusBars = statusBarPx))
        assertContentClearsStatusBar()
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail reader arm consumes the top inset`() {
        composeShell(insets(statusBars = statusBarPx), currentRoute = READER_ROUTE)
        assertContentClearsStatusBar()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `full rail arm consumes the top inset`() {
        composeShell(insets(statusBars = statusBarPx))
        assertContentClearsStatusBar(strip = spacing.statusBar)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `full rail reader arm consumes the top inset`() {
        composeShell(insets(statusBars = statusBarPx), currentRoute = READER_ROUTE)
        assertContentClearsStatusBar()
    }
}
