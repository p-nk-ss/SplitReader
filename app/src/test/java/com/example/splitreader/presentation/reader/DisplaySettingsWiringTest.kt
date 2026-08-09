package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import com.example.splitreader.screenshot.ScreenFixtures
import com.example.splitreader.screenshot.TABLET
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the one line that decides which split slider the user gets: `vertical = vertical`, passed
 * from `ReaderContent` into `DisplaySettingsDialog`.
 *
 * Task 7's review found that line untested by anything. Both dialog goldens call
 * `DisplaySettingsDialog` **directly** with a hand-written `vertical` argument — deliberately, to
 * isolate the dialog's own branch — so neither traverses the call site. Drop the argument, swap it,
 * or hardcode it, and every test in the repo still passed.
 *
 * This test opens the dialog the way a user does, through `ReaderContent`'s own
 * `showDisplaySettings` state, and asserts on the label. It is not a golden: the question is which
 * of two mutually exclusive rows renders, which text semantics answer exactly and pixels answer
 * only incidentally.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DisplaySettingsWiringTest {

    @get:Rule val composeRule = createComposeRule()

    /** Narrow window + translation on = vertical mode, so the dialog must offer "Vertical split". */
    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `the stacked layout gets the vertical split slider`() {
        composeReader()

        composeRule.onNodeWithTag(READER_DISPLAY_SETTINGS_BUTTON).performClick()
        composeRule.waitForIdle()

        // assertExists, not assertIsDisplayed: the dialog scrolls and the split row sits below
        // the fold. The question is which of the two mutually exclusive rows was COMPOSED.
        composeRule.onNodeWithText("Vertical split").assertExists()
        composeRule.onAllNodesWithText("Split position").assertCountEquals(0)
    }

    /**
     * The same reader at tablet width is side-by-side, so it must offer the horizontal
     * "Split position" instead. Two assertions in opposite directions, because a hardcoded flag
     * would satisfy exactly one of them.
     */
    @Test
    @Config(qualifiers = TABLET)
    fun `the side-by-side layout gets the horizontal split slider`() {
        composeReader()

        composeRule.onNodeWithTag(READER_DISPLAY_SETTINGS_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Split position").assertExists()
        composeRule.onAllNodesWithText("Vertical split").assertCountEquals(0)
    }

    private fun composeReader() {
        composeRule.setContent {
            SplitReaderTheme {
                ReaderContent(
                    state = ScreenFixtures.readerContentState.copy(portraitHintDismissed = true),
                    onNavigateBack = {},
                    onSelectChapter = {},
                    onSetTargetLanguage = {},
                    onSetReaderTheme = {},
                    onAdjustTextSize = {},
                    onAdjustLineHeight = {},
                    onSetReadingFont = {},
                    onSetLetterSpacing = {},
                    onSetTextIndent = {},
                    onSetParagraphSpacing = {},
                    onSetJustifyText = {},
                    onSetSplitRatio = {},
                    onSetVerticalSplitRatio = {},
                    onDismissPortraitHint = {},
                    onToggleTranslation = {},
                    onToggleIllustrations = {},
                    onSetNavigationSide = {},
                    onSetHorizontalMargin = {},
                    onSetOrientationLock = {},
                    onUpdateScrollPosition = { _, _, _ -> },
                    onMarkFinished = {},
                    onToggleBookmark = {},
                    onRemoveBookmark = { _, _ -> },
                    onJumpToBookmark = { _, _ -> },
                    onConsumeScrollRestore = {},
                    onVisibleRange = { _, _, _, _ -> },
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onSelectWord = { _, _, _, _, _ -> },
                    onClearWordSelection = {},
                    onSelectionDragged = { _, _ -> },
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
