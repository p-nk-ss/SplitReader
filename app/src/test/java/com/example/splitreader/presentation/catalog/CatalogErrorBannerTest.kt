package com.example.splitreader.presentation.catalog

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * I-2 (v31 review): the catalog showed its error only when the list was empty. A download always
 * starts from a visible row, so every download error was invisible (the spinner just stopped), and
 * a failed search left the popular list on screen labelled "RESULTS". With the Gutenberg fallback
 * (slow mirror, Gutendex timeouts) that is the routine path, so the error is a banner above the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogErrorBannerTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var retries = 0
    private var dismissals = 0

    private fun show(state: CatalogUiState) = composeRule.setContent {
        SplitReaderTheme {
            CatalogScreen(
                uiState = state,
                driveState = DriveUiState(),
                onQueryChange = {},
                onSourceSelected = {},
                onDownload = {},
                onRetry = { retries++ },
                onPickFromDrive = {},
                onSignInWithGoogle = {},
                onDismissError = { dismissals++ },
            )
        }
    }

    @Test
    fun `a download error with the list on screen is shown above the list and can be dismissed`() {
        show(ScreenFixtures.catalogRich.copy(errorMessage = "Download failed: HTTP 404"))

        composeRule.onNodeWithTag(CATALOG_ERROR_BANNER_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Download failed: HTTP 404").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("The Adventures of Sherlock Holmes").assertIsDisplayed()

        composeRule.onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun `a failed search with the popular list on screen shows its error with Retry, and the list stays POPULAR`() {
        show(
            ScreenFixtures.catalogRich.copy(
                query = "dickens",
                listQuery = "",
                errorMessage = "This catalog is temporarily unavailable. Try again later.",
                searchFailed = true,
            ),
        )

        composeRule.onNodeWithText("This catalog is temporarily unavailable. Try again later.").assertIsDisplayed()
        composeRule.onNodeWithText("POPULAR").assertIsDisplayed()
        composeRule.onNodeWithText("RESULTS").assertDoesNotExist()

        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `no error, no banner`() {
        show(ScreenFixtures.catalogRich)

        composeRule.onNodeWithTag(CATALOG_ERROR_BANNER_TAG).assertDoesNotExist()
    }
}
