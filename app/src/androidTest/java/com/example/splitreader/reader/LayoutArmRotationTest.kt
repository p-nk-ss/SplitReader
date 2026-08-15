package com.example.splitreader.reader

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.presentation.reader.ReaderContent
import com.example.splitreader.presentation.reader.ReaderUiState
import com.example.splitreader.presentation.reader.VERTICAL_TOP_PANE
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A **real** configuration change must move the reader between its two arms.
 *
 * The JVM suite only ever simulates this with `@Config(qualifiers = ...)`, which picks a
 * configuration before composition rather than changing one underneath a live composition. This
 * device is 427dp wide in portrait (stacked) and 952dp in landscape (side-by-side), so one rotation
 * crosses the 600dp threshold in both directions.
 *
 * Composes [ReaderContent] rather than [com.example.splitreader.presentation.reader.VerticalBookSpread]
 * directly (unlike [ReaderGestureTest]'s `composeSpread`): the `isCompactWidth` branch that picks
 * between the stacked and side-by-side arms lives in `ReaderContent`, one level up from the spread.
 * Composing the spread directly was tried first and it was uninformative either way — see the task
 * report for the diagnostic that showed why (recreation tore down that composition entirely, so
 * *both* panes vanished regardless of width, not just the arm that should have gone away).
 *
 * NOT covered here, deliberately: scroll-position preservation across the recreation. That belongs
 * to the full app — the states are hoisted from a view model there and from the test here — and it
 * stays on the device checklist.
 */
@RunWith(AndroidJUnit4::class)
class LayoutArmRotationTest : ReaderGestureTest() {

    private val topPaneNode get() = composeRule.onNodeWithTag(VERTICAL_TOP_PANE)

    @After
    fun restorePortrait() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /**
     * Composes the full reader screen — top bar, panes, footer, dialogs — with hoisted states, so
     * the `isCompactWidth` branch in `ReaderContent` is actually exercised by a rotation.
     */
    private fun composeReaderContent() {
        composeRule.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        }
        composeRule.setContent {
            SplitReaderTheme {
                ReaderContent(
                    state = ReaderUiState.Success(
                        book = fixtureBook,
                        currentChapterIndex = 0,
                        sourceLanguage = Language.ENGLISH,
                        targetLanguage = Language.RUSSIAN,
                        translationState = TranslationState.Idle,
                        chapterTranslations = fixtureTranslations,
                        portraitHintDismissed = true,
                    ),
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
                    onDismissPortraitHint = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun rotatingToLandscapeLeavesTheStackedArm() {
        assertPortraitStacked()
        composeReaderContent()
        topPaneNode.assertExists()

        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()

        val widthDp = composeRule.activity.resources.configuration.screenWidthDp
        assertTrue(
            "After rotating, the window is ${widthDp}dp wide — the rotation did not take effect, " +
                "so this test proves nothing about the arm switch.",
            widthDp >= 600,
        )
        topPaneNode.assertDoesNotExist()
    }
}
