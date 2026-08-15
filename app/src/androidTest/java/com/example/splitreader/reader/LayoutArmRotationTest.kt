package com.example.splitreader.reader

import android.content.pm.ActivityInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * (a) What this pins: rotating this device recreates the Activity, and the post-recreation
 * `Configuration` genuinely reports the new window width — 427dp portrait to 952dp landscape,
 * crossing the 600dp `isCompactWidth` threshold. The JVM suite's `@Config(qualifiers = ...)` only
 * ever *picks* a configuration before composition; it structurally cannot demonstrate that a live
 * recreation delivers one. This test can, and does: `screenWidthDp` is read from
 * `composeRule.activity` *after* `requestedOrientation` is flipped and `waitForIdle()` returns.
 *
 * (b) The harness limit, measured rather than assumed: `AndroidComposeTestRule<ComponentActivity>`
 * does not re-run `setContent` after that recreation — the pre-rotation `ComposeView` is destroyed
 * with the old Activity, and nothing re-attaches a composition to the new one. Any node lookup
 * after rotation throws `IllegalStateException: No compose hierarchies found in the app`, and
 * `assertDoesNotExist()` — the one query that does not throw on an empty hierarchy — passes
 * vacuously as a result. This was proven, not assumed: forcing `ReaderScreen.kt`'s
 * `val vertical = isCompactWidth(maxWidth) && state.showTranslation` to
 * `val vertical = true && state.showTranslation` (deliberately breaking the arm switch) and
 * re-running still left a `topPane.assertDoesNotExist()` assertion green — it could not tell a
 * switched arm from a torn-down composition. That assertion has been removed from this test rather
 * than kept as decoration; see `task-6-report.md` for the paired runs (predicate correct, predicate
 * forced) that both passed it.
 *
 * (c) Consequence: whether a real rotation actually swaps the stacked pane for the side-by-side one
 * stays on the **human device checklist**, not in this suite. Automating it requires first fixing
 * the harness — either an Activity that survives recreation in place (e.g. `android:configChanges`
 * covering orientation/screenSize so Compose's own recomposition runs instead of a teardown) or
 * driving the width some other way that does not destroy the composition — not adding more
 * assertions to this test.
 */
@RunWith(AndroidJUnit4::class)
class LayoutArmRotationTest : ReaderGestureTest() {

    @After
    fun restorePortrait() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun realRotationDeliversTheNewConfiguration() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)
        topPane.assertExists()

        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()

        val widthDp = composeRule.activity.resources.configuration.screenWidthDp
        assertTrue(
            "After rotating, the window is ${widthDp}dp wide — the rotation did not take effect.",
            widthDp >= 600,
        )
    }
}
