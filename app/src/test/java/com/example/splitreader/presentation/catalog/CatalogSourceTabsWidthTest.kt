package com.example.splitreader.presentation.catalog

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a phone the last source pill ("Google Drive") was cut off by the screen edge of a horizontally
 * scrolled row, so readers did not know Drive import exists. The pills wrap instead: every one is
 * fully on screen, on a second line if needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class CatalogSourceTabsWidthTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun assertEveryPillOnScreen() {
        composeRule.setContent {
            SplitReaderTheme {
                CatalogScreen(
                    uiState = ScreenFixtures.catalogRich,
                    driveState = DriveUiState(),
                    onQueryChange = {},
                    onSourceSelected = {},
                    onDownload = {},
                    onRetry = {},
                    onPickFromDrive = {},
                    onSignInWithGoogle = {},
                )
            }
        }
        val screenRight: Dp = composeRule.onRoot().getUnclippedBoundsInRoot().right
        val labels = CatalogSource.entries.map { it.displayName } + "Google Drive"
        labels.forEach { label ->
            val bounds = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue(
                "\"$label\" pill ends at ${bounds.right}, past the screen edge $screenRight",
                bounds.left >= Dp(0f) && bounds.right <= screenRight,
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `on a 412 dp phone every source pill is fully on screen`() = assertEveryPillOnScreen()

    @Test
    @Config(qualifiers = "w360dp-h740dp-xhdpi")
    fun `on a 360 dp phone every source pill is fully on screen`() = assertEveryPillOnScreen()
}
