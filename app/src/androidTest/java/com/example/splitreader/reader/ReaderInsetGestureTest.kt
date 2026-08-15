package com.example.splitreader.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads the insets the **system** reports, not ones a test made up.
 *
 * The JVM suite dispatches synthetic `WindowInsetsCompat`, which proves the code reacts correctly
 * to what it is told, not that the system tells it that. The golden suite dispatches nothing at
 * all, so every inset query there resolves to 0 — it is structurally incapable of catching an inset
 * regression, and a doubled top inset shipped through both.
 */
@RunWith(AndroidJUnit4::class)
class ReaderInsetGestureTest : ReaderGestureTest() {

    @Test
    fun bottomPaneClearsTheRealNavigationInset() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        val navPx = realInsets().getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        assertTrue(
            "The system reports a 0px bottom inset, so this test cannot prove anything. The " +
                "emulator is probably in a navigation mode without a bar — check with " +
                "`adb shell cmd overlay list | grep navbar`.",
            navPx > 0,
        )

        val paneBottom = bottomPane.getUnclippedBoundsInRoot().bottom
        val rootBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val gapPx = with(composeRule.density) { (rootBottom - paneBottom).toPx() }

        assertTrue(
            "The bottom pane ends ${gapPx}px above the window bottom, but the real navigation " +
                "inset is ${navPx}px — the last line of the translation is under the system bar.",
            gapPx >= navPx - 1f,
        )
    }

    private fun realInsets(): WindowInsetsCompat {
        lateinit var insets: WindowInsetsCompat
        composeRule.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            insets = ViewCompat.getRootWindowInsets(root)
                ?: WindowInsetsCompat.Builder().build()
        }
        return insets
    }
}
