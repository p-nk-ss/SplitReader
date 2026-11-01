package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Found on a Pixel 7 (411dp wide): reader chrome that fitted the emulator's wider screen did not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths; legacy mode measures text near 0 and everything fits
class PhoneChromeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = ScreenFixtures.readerContentState.copy(
        position = ReadingPosition(ScreenFixtures.readerBook.chapters.lastIndex, 0, 0),
    )

    private fun assertPercentOnScreen() {
        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.fillMaxWidth().testTag("screen")) { ReaderStatusFooter(state, bookEndVisible = true) }
            }
        }
        composeRule.onNodeWithText("100%").assertIsDisplayed()
        val screen = composeRule.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        val percent = composeRule.onNodeWithTag(FOOTER_PERCENT_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("percent ${percent.left}..${percent.right} must be inside 0..${screen.right}", percent.width > 0f && percent.right <= screen.right)
    }

    /** The fixed 200dp progress rule pushed the percentage off the screen. */
    @Test
    @Config(fontScale = 1.15f)
    fun `the footer's percentage fits a phone`() = assertPercentOnScreen()

    @Test
    @Config(fontScale = 2.0f)
    fun `the footer's percentage fits a phone with a large font`() = assertPercentOnScreen()

    /** The top bar was a fixed 48dp: with a large font the language chip was cut at the bottom. */
    @Test
    @Config(fontScale = 2.0f)
    fun `the top bar holds the language chip with a large font`() {
        composeRule.setContent {
            SplitReaderTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.testTag("bar")) { ReaderTopBar(state, {}, {}, {}, {}, {}, {}) }
                }
            }
        }
        // A clipped node reports clipped bounds, so test the cause: the bar must grow past 48dp to fit
        // the chip (a fixed 48dp bar cut it). The chip's text is 11sp x 2 plus 7dp padding each side.
        val bar = composeRule.onNodeWithTag("bar").fetchSemanticsNode().size.height
        val density = composeRule.activity.resources.displayMetrics.density
        assertTrue("bar height ${bar / density}dp must exceed 48dp", bar / density > 48f)
    }

    /** The hint had no button: tapping outside it was the only way out. */
    @Test
    fun `the portrait hint closes with Got it, keeping the checkbox`() {
        val dismissed = mutableListOf<Boolean>()
        composeRule.setContent { SplitReaderTheme { PortraitHintDialog(onDismiss = { dismissed += it }) } }
        composeRule.onNodeWithText("Don't show again").performClick()
        composeRule.onNodeWithText("GOT IT").performClick()
        assertEquals(listOf(true), dismissed)
    }
}
