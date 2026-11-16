package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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

/**
 * A selection handle (the hit zone at either end of the selected range in [ParagraphItem]) only
 * drags. A tap on it closed the word popup (the paragraph's tap = dismiss, E2E case POP-007) and a
 * still press of ~0.5 s re-selected the word under the finger and sent a new translation request —
 * which can bill a paid provider. The paragraph's own tap/long-press detector is inner to the
 * handle's drag handler, so it saw the press first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h900dp-xhdpi")
class SelectionHandleGestureTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val text = "Hello world and a few more words"

    private var taps = 0
    private var selected = 0
    private var dragged = 0
    private var dragEnds = 0

    private fun show() {
        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.width(400.dp).testTag("para")) {
                    ParagraphItem(
                        text = text,
                        index = 0,
                        isFirstOfChapter = false,
                        isOriginal = true,
                        isActive = true,
                        selectedWordStart = 0,
                        selectedWordEnd = 5, // "Hello"
                        style = ScreenFixtures.readerContentState.readingStyle,
                        wordHighlightEnabled = true,
                        onWordSelected = { _, _, _ -> selected++ },
                        onSelectionDragged = { _, _ -> dragged++ },
                        onSelectionDragEnd = { dragEnds++ },
                        onTap = { taps++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * The start handle of the selection "Hello" (0..5): under its first letter, at the bottom of the
     * single line — well inside the 28 dp hit radius, far from the line's right end.
     */
    private fun androidx.compose.ui.test.TouchInjectionScope.handleAt() = Offset(left + 4f, bottom - 2f)

    @Test
    fun `a tap on a handle keeps the selection and its popup`() {
        show()
        composeRule.onNodeWithTag("para").performTouchInput { click(handleAt()) }
        composeRule.waitForIdle()
        assertEquals("tap must not reach the paragraph (it dismisses the popup)", 0, taps)
        assertEquals("tap must not select a word", 0, selected)
    }

    @Test
    fun `a still press on a handle does not re-select a word or request a translation`() {
        show()
        // A finger held still: no move events at all (longClick() injects one, which hid the bug).
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("para").performTouchInput { down(handleAt()) }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.onNodeWithTag("para").performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("a still press must not re-select (a new translation request)", 0, selected)
        assertEquals("a still press must not dismiss the popup", 0, taps)
        assertEquals("nothing moved", 0, dragged)
    }

    @Test
    fun `dragging a handle still moves the selection`() {
        show()
        composeRule.onNodeWithTag("para").performTouchInput {
            val from = handleAt()
            down(from)
            moveTo(Offset(right - 4f, from.y))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("drag moved the start handle", dragged > 0)
        assertEquals("one release", 1, dragEnds)
        assertEquals(0, taps)
        assertEquals(0, selected)
    }

    @Test
    fun `a tap on the paragraph away from the handles still reaches it`() {
        show()
        composeRule.onNodeWithTag("para").performTouchInput { click(Offset(right - 4f, centerY)) }
        composeRule.waitForIdle()
        assertEquals(1, taps)
    }
}
