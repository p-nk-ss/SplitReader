package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [VerticalBookSpread]'s stacked layout (original above, translation below) had no page-level
 * catch-all: unlike [BookSpread] (see [PageTapTest]), tapping the gap between rows, the margin
 * beside a short line, or the space below the last row did nothing in either pane — found on a
 * phone, and in E2E on a phone too. The fix mirrors `pageTap` into both panes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-xhdpi")
class VerticalPageTapTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "The first chapter", paragraphs = listOf("Short line."))),
    )

    private var toggles = 0
    private var dismisses = 0
    private var wordSelectionForContent: WordSelection? = null

    private fun show() {
        composeRule.setContent {
            SplitReaderTheme {
                VerticalBookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = book,
                    chapterTranslations = mapOf(0 to listOf("t0")),
                    showIllustrations = true,
                    verticalSplitRatio = 0.5f,
                    horizontalMargin = ReadingDefaults.HORIZONTAL_MARGIN,
                    style = ScreenFixtures.readerContentState.readingStyle,
                    wordSelection = wordSelectionForContent,
                    wordHighlightEnabled = true,
                    listState = LazyListState(),
                    translationListState = LazyListState(),
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSelectionDragEnd = {},
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onDismiss = { dismisses++ },
                    onToggleBars = { toggles++ },
                    onSetVerticalSplitRatio = {},
                    barsVisible = false,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                )
            }
        }
    }

    @Test
    fun `a tap on empty space in the top pane toggles the bars`() {
        show()
        // The right margin / tail below the short paragraph — nothing else claims it.
        composeRule.onNodeWithTag(VERTICAL_TOP_PANE).performTouchInput {
            click(Offset(right - 10f, centerY))
        }
        assertEquals(1, toggles)
        assertEquals(0, dismisses)
    }

    @Test
    fun `a tap on empty space in the bottom pane toggles the bars`() {
        show()
        composeRule.onNodeWithTag(VERTICAL_BOTTOM_PANE).performTouchInput {
            click(Offset(right - 10f, centerY))
        }
        assertEquals(1, toggles)
        assertEquals(0, dismisses)
    }

    @Test
    fun `a tap on the space below the last row in the top pane toggles the bars`() {
        show()
        composeRule.onNodeWithTag(VERTICAL_TOP_PANE).performTouchInput {
            click(Offset(centerX, bottom - 10f))
        }
        assertEquals(1, toggles)
        assertEquals(0, dismisses)
    }

    @Test
    fun `a tap on the space below the last row in the bottom pane toggles the bars`() {
        show()
        composeRule.onNodeWithTag(VERTICAL_BOTTOM_PANE).performTouchInput {
            click(Offset(centerX, bottom - 10f))
        }
        assertEquals(1, toggles)
        assertEquals(0, dismisses)
    }

    @Test
    fun `a tap on the original paragraph still toggles exactly once`() {
        show()
        composeRule.onNodeWithText("Short line.").performClick()
        assertEquals(1, toggles)
        assertEquals(0, dismisses)
    }

    @Test
    fun `a tap on the translated paragraph still toggles exactly once`() {
        show()
        composeRule.onNodeWithText("t0").performClick()
        assertEquals(1, toggles)
        assertEquals(0, dismisses)
    }

    @Test
    fun `with a word selection open, an empty-space tap in the top pane dismisses it instead of toggling`() {
        wordSelectionForContent = WordSelection(
            word = "Short",
            chapterIndex = 0,
            paragraphIndex = 0,
            startChar = 0,
            endChar = 5,
        )
        show()
        composeRule.onNodeWithTag(VERTICAL_TOP_PANE).performTouchInput {
            click(Offset(right - 10f, centerY))
        }
        assertEquals(0, toggles)
        assertEquals(1, dismisses)
    }

    @Test
    fun `with a word selection open, an empty-space tap in the bottom pane dismisses it instead of toggling`() {
        wordSelectionForContent = WordSelection(
            word = "Short",
            chapterIndex = 0,
            paragraphIndex = 0,
            startChar = 0,
            endChar = 5,
        )
        show()
        composeRule.onNodeWithTag(VERTICAL_BOTTOM_PANE).performTouchInput {
            click(Offset(right - 10f, centerY))
        }
        assertEquals(0, toggles)
        assertEquals(1, dismisses)
    }
}
