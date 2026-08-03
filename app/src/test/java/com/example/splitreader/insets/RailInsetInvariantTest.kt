package com.example.splitreader.insets

import com.example.splitreader.presentation.navigation.ShellTestTags
import com.example.splitreader.screenshot.PHONE_LANDSCAPE
import com.example.splitreader.screenshot.TABLET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * I2 and I3, the rail invariants.
 *
 * I2 encodes the rule the shell got wrong twice in Phase 2b: chrome backgrounds reach the physical
 * screen edge and only their *content* is inset. Applying the cutout inset at the root instead
 * satisfies "icons clear the hole" while leaving a bare band beside the rail.
 */
class RailInsetInvariantTest : ShellInsetTest() {

    private val cutoutPx = 88

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail background reaches the edge while its avatar clears the cutout`() {
        composeShell(shellInsets(statusBars = 0, cutoutLeft = cutoutPx))

        val rail = boundsOf(ShellTestTags.COMPACT_RAIL)
        assertEquals(
            "The rail's background must reach the physical edge; a non-zero left means the inset " +
                "was applied outside the rail and a bare band will show beside it.",
            0f,
            rail.left,
            0.5f,
        )

        val avatar = boundsOf(ShellTestTags.RAIL_AVATAR)
        assertTrue(
            "Rail content starts at ${avatar.left}, inside the ${cutoutPx}px cutout — it would be " +
                "drawn under the camera hole.",
            avatar.left >= cutoutPx,
        )
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail contains its own last child`() {
        composeShell(shellInsets(statusBars = 63, navigationBars = 48))

        val rail = boundsOf(ShellTestTags.COMPACT_RAIL)
        val avatar = boundsOf(ShellTestTags.RAIL_AVATAR)
        assertTrue(
            "The rail's last child ends at ${avatar.bottom} but the rail ends at ${rail.bottom} — " +
                "it is being clipped.",
            avatar.bottom <= rail.bottom,
        )
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `full rail contains its own last child`() {
        composeShell(shellInsets(statusBars = 63, navigationBars = 48))

        val rail = boundsOf(ShellTestTags.FULL_RAIL)
        val avatar = boundsOf(ShellTestTags.RAIL_AVATAR)
        assertTrue(
            "The rail's last child ends at ${avatar.bottom} but the rail ends at ${rail.bottom} — " +
                "it is being clipped.",
            avatar.bottom <= rail.bottom,
        )
    }
}
