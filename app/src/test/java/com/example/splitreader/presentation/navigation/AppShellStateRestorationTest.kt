package com.example.splitreader.presentation.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rotating the device recreates the activity, and the shell can land in a different arm (bottom
 * bar ↔ icon rail ↔ full rail). Everything the screens keep in `rememberSaveable` — the reader's
 * scroll position above all — must survive that: found on a device, where turning a phone to
 * landscape showed the start of the book, because `content()` was composed from a different
 * `when` branch per arm and so restored under a different saveable key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppShellStateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val phonePortrait = DpSize(360.dp, 800.dp)
    private val phoneLandscape = DpSize(800.dp, 360.dp) // wide but short: the icon-rail arm
    private val tablet = DpSize(900.dp, 1200.dp)        // the full-rail arm

    private fun survivesArmChange(route: String, from: DpSize, to: DpSize) {
        val tester = StateRestorationTester(composeRule)
        var size = from
        var seed = 3 // the value the screen holds before rotating
        tester.setContent {
            SplitReaderTheme {
                Box(Modifier.requiredSize(size)) {
                    AppShell(
                        currentRoute = route,
                        avatarLabel = "P",
                        avatarSubtitle = "",
                        onNavigateToHome = {},
                        onNavigateToCatalog = {},
                        onNavigateToAlmanac = {},
                        onNavigateToWords = {},
                        onNavigateToSettings = {},
                        onNavigateToAccount = {},
                    ) {
                        val n by rememberSaveable { mutableIntStateOf(seed) }
                        Text("n=$n", Modifier.testTag("counter"))
                    }
                }
            }
        }
        composeRule.onNodeWithTag("counter").assertTextEquals("n=3")

        size = to   // the activity comes back in the other orientation…
        seed = 0    // …so a fresh (unrestored) state would read 0
        tester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("counter").assertTextEquals("n=3")
    }

    /** Control: the harness itself restores state when the arm does not change. */
    @Test
    fun `state survives recreation in the same orientation`() =
        survivesArmChange("reader?path=x", phonePortrait, phonePortrait)

    @Test
    fun `reader state survives turning a phone to landscape`() =
        survivesArmChange("reader?path=x", phonePortrait, phoneLandscape)

    @Test
    fun `reader state survives turning a phone back to portrait`() =
        survivesArmChange("reader?path=x", phoneLandscape, phonePortrait)

    @Test
    fun `library state survives the bottom-bar to full-rail change`() =
        survivesArmChange(HOME_ROUTE, phonePortrait, tablet)
}
