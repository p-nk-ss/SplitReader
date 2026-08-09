package com.example.splitreader.insets

import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.splitreader.presentation.navigation.ShellTestTags
import com.example.splitreader.presentation.theme.RAIL_MIN_HEIGHT
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
        composeShell(insets(statusBars = 0, cutoutLeft = cutoutPx))

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

    /**
     * The same invariant for the *other* rail. Both rails consume the Start inset themselves, and
     * both got it wrong in Phase 2b; covering only the icon rail left the full rail's copy of
     * defect #3 (icons under the camera hole) free to come back unnoticed.
     */
    @Test
    @Config(qualifiers = TABLET)
    fun `full rail background reaches the edge while its avatar clears the cutout`() {
        composeShell(insets(statusBars = 0, cutoutLeft = cutoutPx))

        val rail = boundsOf(ShellTestTags.FULL_RAIL)
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

    /**
     * I3, and the assertion that actually pins Phase 2b defect #2 — the full rail rendered in a
     * window too short for it, clipping its lower items away.
     *
     * Two things had to change from the original `avatar.bottom <= rail.bottom`:
     *
     * **The inequality could not fail.** `Column` measures each child against the space left over
     * (`RowColumnMeasurePolicy`), so the children's summed extent cannot exceed the Column's
     * height: a squeezed child can never overhang its parent, which is the only thing the old
     * assertion looked for. A squeeze shows up as *loss of natural size* instead, so that is what
     * this asserts. Both degraded forms are real — the sixth-tab break below measures the avatar at
     * 39px, and a severely short window collapses it to `Rect.Zero`. (The Task-4 sweep saw only the
     * zero form and concluded a short rect was impossible. It is not; it just needs a small enough
     * deficit, which is exactly what testing at the boundary produces.)
     *
     * **The window was wrong.** At `TABLET` (h800dp) the rail has ~270dp to spare, so no plausible
     * regression clips it. The defect's real shape is a rail whose content outgrows
     * [RAIL_MIN_HEIGHT] without anyone raising the constant — add a sixth `RailTab` and every
     * window between 530dp and ~584dp silently loses its avatar. That is only visible at the
     * boundary, so this test runs at exactly [RAIL_MIN_HEIGHT], the shortest window the app claims
     * can render the full rail, where the rail's flexible spacer holds just 13px (6.5dp) of slack.
     */
    @Test
    @Config(qualifiers = RAIL_MIN_HEIGHT_WINDOW)
    fun `full rail fits its avatar at the shortest window it claims to support`() {
        composeShell(insets(statusBars = 63, navigationBars = 48))

        // The qualifier is a string literal because @Config demands a compile-time constant, so it
        // cannot read RAIL_MIN_HEIGHT directly. Fail loudly rather than silently testing some other
        // height if the constant moves.
        val windowHeight = with(composeRule.density) {
            composeRule.onRoot().fetchSemanticsNode().size.height.toDp()
        }
        assertEquals(
            "This test's device qualifier ($RAIL_MIN_HEIGHT_WINDOW) must stay exactly " +
                "RAIL_MIN_HEIGHT tall, or it stops testing the boundary it is named for.",
            RAIL_MIN_HEIGHT.value,
            windowHeight.value,
            0.5f,
        )

        // Throws if this window did not select the full-rail arm at all.
        boundsOf(ShellTestTags.FULL_RAIL)

        val avatar = boundsOf(ShellTestTags.RAIL_AVATAR)
        val floorPx = with(composeRule.density) { AVATAR_FIXED_HEIGHT.toPx() }
        assertTrue(
            "The rail's avatar measured ${avatar.height}px tall, below the " +
                "${AVATAR_FIXED_HEIGHT.value}dp (${floorPx}px) its own fixed children occupy — the " +
                "rail's content no longer fits in a RAIL_MIN_HEIGHT window and the last item is " +
                "being squeezed away. Either shrink the rail's content or raise RAIL_MIN_HEIGHT.",
            avatar.height >= floorPx,
        )
    }

    /**
     * NOT an inset invariant, despite the company it keeps, and deliberately renamed from
     * "icon rail contains its own last child" — that name promised clipping coverage this cannot
     * deliver.
     *
     * `avatar.bottom <= rail.bottom` is an identity for this rail twice over: the Column bound
     * above, and `verticalScroll`'s `clipScrollableContainer`, which makes `boundsInRoot` intersect
     * the reported rect with the viewport. And unlike the full rail, clipping here is *correct*
     * behaviour — this rail scrolls by design (see `CompactNavigationRail`'s KDoc), so a
     * scrolled-out avatar legitimately reports a short rect and a height floor would be wrong too.
     *
     * What is left is still worth keeping: that this window selects the icon rail and that the rail
     * and its avatar both render non-degenerate. `boundsOf` throws on a missing tag, so arm
     * misselection — the actual mechanism behind defect #2 — fails here loudly.
     */
    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `icon rail arm renders the rail and its avatar`() {
        composeShell(insets(statusBars = 63, navigationBars = 48))

        val rail = boundsOf(ShellTestTags.COMPACT_RAIL)
        val avatar = boundsOf(ShellTestTags.RAIL_AVATAR)
        assertTrue("The icon rail rendered with an empty rect: $rail", rail.height > 0f)
        assertTrue("The icon rail's avatar rendered with an empty rect: $avatar", avatar.height > 0f)
    }
}

/**
 * A tablet-width window exactly [RAIL_MIN_HEIGHT] tall: the shortest window `AppShell` will still
 * put the full rail in. Kept in sync with the constant by an assertion, not by hope.
 */
private const val RAIL_MIN_HEIGHT_WINDOW = "w1280dp-h530dp-xhdpi"

/**
 * What `RailAvatar` occupies before a single glyph is measured: 4dp of padding top and bottom, the
 * 32dp circle, and the 3dp spacer under it. Deliberately excludes the subtitle `Text`, whose height
 * depends on font metrics — this is a floor the avatar must clear, not its natural height (which
 * measures 69dp here).
 */
private val AVATAR_FIXED_HEIGHT = (4 + 32 + 3 + 4).dp
