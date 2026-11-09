package com.example.splitreader.presentation.catalog

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E2E found the Catalog search field absent from the accessibility tree: its placeholder
 * ("Search by title or author…") was only a sibling Text outside the editable field, so TalkBack
 * got an unlabeled edit box. A contentDescription on the field would label it, but TalkBack may
 * then announce that description instead of what the user typed. So the placeholder is real text
 * INSIDE the field (its decoration box) while the query is empty — the field is labelled by it —
 * and once the user types, the field exposes the typed text and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogSearchFieldAccessibilityTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val placeholder = "Search by title or author…"

    private fun show(query: String) = composeRule.setContent {
        SplitReaderTheme {
            CatalogScreen(
                uiState = ScreenFixtures.catalogRich.copy(query = query),
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

    @Test
    fun `the empty search field is labelled by its placeholder`() {
        show("")

        composeRule.onNode(hasSetTextAction() and hasText(placeholder)).assertIsDisplayed()
    }

    @Test
    fun `once the user has typed, the field exposes the typed text, not a description`() {
        show("holmes")

        composeRule.onNode(hasSetTextAction() and hasText("holmes"))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }
}
