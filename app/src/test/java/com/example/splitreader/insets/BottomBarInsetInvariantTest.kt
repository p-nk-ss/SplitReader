package com.example.splitreader.insets

import androidx.compose.ui.unit.dp
import com.example.splitreader.presentation.navigation.ShellTestTags
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * I4: the bottom bar spans to the bottom edge, and its content sits above the gesture inset.
 *
 * Not a defect that has shipped — the mirror of one that did, pinned before it can.
 *
 * The original pair of assertions here (`bar.bottom > content.bottom` and
 * `bar.height > navPx`) were both insensitive to the defect they named: the first is
 * structurally guaranteed by the shell's Column/weight(1f) layout regardless of what the
 * bar's inset padding does, and the second's floor (`navPx`) sits below the bar's own
 * fixed 64dp content row, so losing the inset's contribution never crosses it. Replaced
 * with assertions that compare against the geometry the invariant actually claims:
 *
 * - A fails when the bar stops consuming the bottom inset (the defect this invariant names) —
 *   verified against the exact expected height, not merely a low floor.
 * - B fails only for a *different, structural* regression: the bar overlaying content instead
 *   of the Column reserving space for it (e.g. content switched from `weight(1f)` to an
 *   absolutely-positioned overlay). It cannot fail from an inset-padding change alone, because
 *   the Column's sequential layout guarantees content ends where the bar begins as long as both
 *   remain ordinary Column siblings — that guarantee is exactly what B is watching for.
 */
class BottomBarInsetInvariantTest : ShellInsetTest() {

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `bottom bar reaches the bottom edge and keeps its content above the gesture inset`() {
        val navPx = 48
        composeShell(shellInsets(statusBars = 63, navigationBars = navPx))

        val bar = boundsOf(ShellTestTags.BOTTOM_BAR)
        val content = boundsOf(ShellTestTags.CONTENT)

        // A: the bar consumes the bottom inset — its height must be its 64dp content row
        // *plus* the inset, not merely non-trivial.
        val expectedPx = with(composeRule.density) { 64.dp.toPx() } + navPx
        assertTrue(
            "The bar is ${bar.height}px but must be its 64dp content row plus the ${navPx}px " +
                "gesture inset (${expectedPx}px) — it is not consuming the bottom inset.",
            bar.height >= expectedPx - 1f,
        )

        // B: the content is not overlapped by the bar.
        assertTrue(
            "The content area ends at ${content.bottom} but the bar starts at ${bar.top} — " +
                "content is running underneath the navigation bar.",
            content.bottom <= bar.top + 1f,
        )
    }
}
