package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.progressAt
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
        val expected = (book.progressAt(state.position) * 100).toInt()
        assertTrue(expected in 1..99)
        composeRule.onNodeWithText("$expected%").assertIsDisplayed()
    }

    /**
     * Found on a tablet: the footer sat under the gesture bar, cut almost in half. The app draws
     * edge to edge, so the footer must lift its content above the navigation bar.
     */
    @Test
    fun `the footer's text stays above the navigation bar`() {
        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    Box(Modifier.align(Alignment.BottomCenter)) { ReaderStatusFooter(state) }
                }
            }
        }
        composeRule.waitForIdle()
        val composeView = composeRule.activity
            .findViewById<android.view.ViewGroup>(android.R.id.content)
            .getChildAt(0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 60))
            .build()
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(composeView, insets) }
        composeRule.waitForIdle()

        val rootBottom = composeRule.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot.bottom
        val textBottom = composeRule.onNodeWithText("CH", substring = true).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("text bottom $textBottom must be above the 60px bar ending at $rootBottom", textBottom <= rootBottom - 60f)
    }

    private fun item(index: Int, offset: Int, size: Int) = object : LazyListItemInfo {
        override val index = index
        override val key: Any = index
        override val offset = offset
        override val size = size
        override val contentType: Any? = null
    }

    // 100 items, the last one end_padding; the viewport is 1000 px tall.
    private fun view(vararg items: LazyListItemInfo, viewportEnd: Int = 1000, scrolledDown: Boolean = true) =
        BookEndView(items.toList(), 100, viewportEnd, scrolledDown)

    @Test
    fun `the end is the bottom of the last row on screen`() {
        assertTrue(bookEndAfter(false, view(item(97, 0, 600), item(98, 600, 400))))
    }

    @Test
    fun `the first lines of a long last paragraph are not the end`() {
        assertFalse(bookEndAfter(false, view(item(97, 0, 900), item(98, 900, 700))))
    }

    @Test
    fun `the row before the last on screen is not the end`() {
        assertFalse(bookEndAfter(false, view(item(96, 0, 500), item(97, 500, 500))))
    }

    @Test
    fun `opening the bars, which pushes the last row's bottom off screen, keeps the end`() {
        // Found on a tablet: the top bar shrinks the list, the footer then read 99%.
        assertTrue(bookEndAfter(true, view(item(97, 100, 600), item(98, 700, 400), viewportEnd = 900)))
    }

    @Test
    fun `scrolling the last row off screen leaves the end`() {
        assertFalse(bookEndAfter(true, view(item(96, 0, 600), item(97, 600, 400))))
    }

    @Test
    fun `a book that fits one screen, or was merely opened, is not finished`() {
        val all = BookEndView(listOf(item(0, 0, 300), item(1, 300, 100)), 3, 1000, scrolledDown = false)
        assertFalse(bookEndAfter(false, all))
        assertFalse(bookEndAfter(true, all))
        assertFalse(bookEndAfter(false, BookEndView(emptyList(), 1, 1000, scrolledDown = true)))
    }
}
