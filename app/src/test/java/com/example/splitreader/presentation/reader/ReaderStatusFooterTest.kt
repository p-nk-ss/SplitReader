package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Found on a device: on the last screen the footer read 95–98% (it counts paragraphs above the top
 * line) while the Library, which marks the book finished there, read 100%.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReaderStatusFooterTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = ScreenFixtures.readerBook
    private val lastChapter = book.chapters.lastIndex
    private val lastParagraph = book.chapters.last().paragraphs.lastIndex
    // The top line on the last screen: the last paragraph, so the count is short of the end.
    private val state = ScreenFixtures.readerContentState.copy(position = ReadingPosition(lastChapter, lastParagraph, 0))

    @Test
    fun `the last screen reads 100 percent, as the Library does`() {
        composeRule.setContent { SplitReaderTheme { ReaderStatusFooter(state, bookEndVisible = true) } }
        composeRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun `before the end the footer counts paragraphs`() {
        composeRule.setContent { SplitReaderTheme { ReaderStatusFooter(state, bookEndVisible = false) } }
        composeRule.onNodeWithText("100%").assertDoesNotExist()
    }

    @Test
    fun `the end is the last two items on screen after scrolling`() {
        assertTrue(isBookEndVisible(lastVisible = 98, total = 100, scrolledDown = true))
        assertFalse(isBookEndVisible(lastVisible = 97, total = 100, scrolledDown = true))
        // A book that fits one screen, or was merely opened, is not finished.
        assertFalse(isBookEndVisible(lastVisible = 99, total = 100, scrolledDown = false))
        assertFalse(isBookEndVisible(lastVisible = -1, total = 0, scrolledDown = true))
    }
}
