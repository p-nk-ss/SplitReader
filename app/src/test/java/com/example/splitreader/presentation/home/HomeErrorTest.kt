package com.example.splitreader.presentation.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * The Library's errors reach the reader (2026-11-04): the ViewModel set "Can't access this file —
 * reopen it via Open book" and import failures, but nothing showed them — a tap on a book whose file
 * had gone did nothing at all (found on a tablet emulator).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-xhdpi")
class HomeErrorTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `an error is shown, and OK dismisses it`() {
        var dismissed = 0
        composeRule.setContent {
            SplitReaderTheme {
                HomeScreen(
                    uiState = ScreenFixtures.homeUiStateRich.copy(errorMessage = "Can't access this file — reopen it via \"Open book\"."),
                    onOpenFilePicker = {}, onOpenFromLibrary = {}, onDeleteBook = {},
                    onDismissError = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithText("Can't access this file", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a tap anywhere on the Continue reading card opens the book, not only its button`() {
        val opened = mutableListOf<String>()
        val state = ScreenFixtures.homeUiStateRich
        composeRule.setContent {
            SplitReaderTheme {
                HomeScreen(
                    uiState = state, onOpenFilePicker = {}, onOpenFromLibrary = { opened += it },
                    onDeleteBook = {}, onDismissError = {},
                )
            }
        }
        // The eyebrow above the title (the button below says the same, in another node).
        composeRule.onAllNodesWithText("CONTINUE READING", useUnmergedTree = true)[0].performClick()
        assertEquals(listOf(state.lastBook!!.uri), opened)
    }
}
