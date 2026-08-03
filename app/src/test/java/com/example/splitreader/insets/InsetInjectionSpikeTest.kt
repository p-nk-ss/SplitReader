package com.example.splitreader.insets

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Feasibility probe, not a regression test.
 *
 * Question: can a Robolectric test deliver non-zero window insets to Compose, so that
 * `statusBarsPadding()` actually offsets content? If yes, the whole inset defect class becomes
 * catchable in the JVM suite. If no, the phase takes the instrumented path.
 *
 * Deliberately self-contained — its own rule and its own @Config — so a probe cannot destabilise
 * the 62-golden screenshot suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InsetInjectionSpikeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `dispatched status bar inset offsets a statusBarsPadding box`() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(10.dp)
                        .testTag("probe"),
                )
            }
        }
        composeRule.waitForIdle()

        val before = composeRule.onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot.top

        // The compose view is the first child of android.R.id.content — dispatch there, NOT at the
        // decor view, whose ancestors may consume the insets before Compose sees them.
        val composeView = composeRule.activity
            .findViewById<android.view.ViewGroup>(android.R.id.content)
            .getChildAt(0)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 63, 0, 0))
            .build()

        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(composeView, insets)
        }
        // Compose updates WindowInsetsHolder on the next composition, not synchronously.
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot.top

        // Exact, not merely directional: the phase's whole go/no-go rests on the dispatched inset
        // arriving at full magnitude, and a directional check would also pass on a 1px shift from
        // some unrelated cause. Insets.of and boundsInRoot are both raw pixels and Compose's
        // inset-padding modifiers consume raw px without a density round trip, so px-for-px is
        // structural here, not a density coincidence.
        assertEquals(
            "Expected the box to move down by exactly the 63px dispatched status-bar inset. " +
                "before=$before after=$after",
            before + 63f,
            after,
            0.5f,
        )
    }
}
