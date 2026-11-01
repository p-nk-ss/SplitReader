package com.example.splitreader.presentation.update

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.UpdateState
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateBannerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clicks = mutableListOf<String>()

    private fun show(state: UpdateState) {
        composeRule.setContent {
            SplitReaderTheme {
                UpdateBanner(
                    state = state,
                    onUpdate = { clicks += "update" },
                    onRestart = { clicks += "restart" },
                    onNotNow = { clicks += "notNow" },
                )
            }
        }
    }

    @Test
    fun `an offer shows Update and Not now`() {
        show(UpdateState.Available(10, snoozed = false))
        composeRule.onNodeWithText("A new version of Mirrolit is available").assertIsDisplayed()
        composeRule.onNodeWithText("UPDATE").performClick() // LibraryTagButton uppercases
        composeRule.onNodeWithText("Not now").performClick()
        assertEquals(listOf("update", "notNow"), clicks)
    }

    @Test
    fun `a snoozed offer shows nothing`() {
        show(UpdateState.Available(10, snoozed = true))
        assertTrue(composeRule.onRoot().fetchSemanticsNode().children.isEmpty())
    }

    @Test
    fun `a download shows its progress, with no buttons`() {
        show(UpdateState.Downloading(42))
        composeRule.onNodeWithText("Downloading update… 42%").assertIsDisplayed()
        composeRule.onNodeWithText("UPDATE").assertDoesNotExist()
    }

    @Test
    fun `a finished download offers Restart`() {
        show(UpdateState.Downloaded)
        composeRule.onNodeWithText("Update ready — restart to finish").assertIsDisplayed()
        composeRule.onNodeWithText("RESTART").performClick()
        assertEquals(listOf("restart"), clicks)
    }

    @Test
    fun `no update shows nothing`() {
        show(UpdateState.None)
        assertTrue(composeRule.onRoot().fetchSemanticsNode().children.isEmpty())
    }
}
