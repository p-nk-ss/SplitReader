package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import com.example.splitreader.screenshot.ScreenFixtures
import com.example.splitreader.screenshot.TABLET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chapter picker used to scroll the list itself (`listState.scrollToItem`), next to the one
 * jump path every other jump takes (`pendingJump` -> `scrollToItemAndHold`): no re-anchoring while
 * rows settle, and a jump still holding was only ended by accident. The screen now only reports the
 * choice; the list moves when -- and only when -- the state asks for the jump.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ReaderChapterJumpTest {

    @get:Rule val composeRule = createComposeRule()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = List(3) { c ->
            Chapter(index = c, title = "Chapter $c", paragraphs = List(40) { p -> "Paragraph $p of chapter $c. ".repeat(6) })
        },
    )

    private val chosen = mutableListOf<Int>()
    private val positions = mutableListOf<ReadingPosition>()
    private var state by mutableStateOf(
        ScreenFixtures.readerContentState.copy(
            book = book, position = ReadingPosition.START, chapterTranslations = emptyMap(),
            bookmarks = emptyList(), pendingJump = null, portraitHintDismissed = true,
        ),
    )

    private fun pickChapter(index: Int) {
        composeRule.onNodeWithTag(READER_CHAPTERS_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(chapterRowTag(index)).performClick()
        settle()
    }

    /** Past the 300 ms debounce of the screen's position report. */
    private fun settle() {
        // First let a state write made by the test reach the composition, then run the clock.
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `the picker only reports the choice, the list does not move on its own`() {
        composeReader()
        settle()
        positions.clear()

        pickChapter(2)

        assertEquals(listOf(2), chosen)
        assertEquals("the screen scrolled without being asked to jump", emptyList<ReadingPosition>(), positions)
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `a chapter jump lands on the chapter's first paragraph - stacked`() = chapterJumpLands()

    @Test
    @Config(qualifiers = TABLET)
    fun `a chapter jump lands on the chapter's first paragraph - side by side`() = chapterJumpLands()

    private fun chapterJumpLands() {
        var consumed = 0
        composeReader(onConsume = { consumed++; state = state.copy(pendingJump = null) })
        settle()

        state = state.copy(pendingJump = ReaderJump(ReadingPosition(2, 0, 0), atChapterStart = true))
        settle()

        assertEquals(1, consumed)
        // The masthead is the top row; the position it names is the chapter's first paragraph (ADR 0001).
        assertEquals("reported: $positions", ReadingPosition(2, 0, 0), positions.last())
        // ...which a jump to paragraph 0 itself reports too. Only the masthead on screen tells the two
        // apart: landing on the paragraph leaves the chapter's number and title above the page.
        val mastheads = composeRule.onAllNodesWithText("— 3 —")
        assertTrue("chapter 3's masthead is not on the page", mastheads.fetchSemanticsNodes().isNotEmpty())
        mastheads.onFirst().assertIsDisplayed()
    }

    private fun composeReader(onConsume: () -> Unit = {}) {
        composeRule.setContent {
            SplitReaderTheme {
                ReaderContent(
                    state = state,
                    onNavigateBack = {},
                    onSelectChapter = { chosen += it },
                    onSetTargetLanguage = {},
                    onAppearanceChange = {},
                    onDismissPortraitHint = {},
                    onReadingPositionChanged = { positions += it },
                    onMarkFinished = {},
                    onToggleBookmark = {},
                    onRemoveBookmark = { _, _ -> },
                    onJumpToBookmark = { _, _ -> },
                    onConsumePendingJump = onConsume,
                    onVisibleRange = { _, _, _, _ -> },
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onSelectWord = { _, _, _, _, _ -> },
                    onClearWordSelection = {},
                    onSelectionDragged = { _, _ -> },
                    onSelectionDragEnd = {},
                    onSelectProvider = {},
                    onConfigureProvider = { _, _, _ -> },
                    onClearProvider = {},
                    onRefreshTranslationUsage = {},
                    onResetTranslationUsage = {},
                    onRetryTranslation = {},
                    onTranslateWholeChapter = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
