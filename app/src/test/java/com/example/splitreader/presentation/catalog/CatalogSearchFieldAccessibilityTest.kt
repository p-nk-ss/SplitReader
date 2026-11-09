package com.example.splitreader.presentation.catalog

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E2E found the Catalog search field absent from the accessibility tree: its placeholder
 * ("Search by title or author…") is only a sibling [androidx.compose.material3.Text] that
 * disappears once the user types, so TalkBack never gets a stable label for the actual editable
 * field — Maestro had to tap it by screen position instead of by label. The field now carries that
 * text as a `semantics { contentDescription = … }`, so it is findable regardless of its contents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogSearchFieldAccessibilityTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the search field has an accessible label`() {
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

        composeRule.onNodeWithContentDescription("Search by title or author…").assertIsDisplayed()
    }

    @Test
    fun `the label survives after the user has typed a query`() {
        composeRule.setContent {
            SplitReaderTheme {
                CatalogScreen(
                    uiState = ScreenFixtures.catalogRich.copy(query = "holmes"),
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

        // With text present, the placeholder Text is gone — only the semantics label is left.
        composeRule.onNodeWithContentDescription("Search by title or author…").assertIsDisplayed()
    }
}
