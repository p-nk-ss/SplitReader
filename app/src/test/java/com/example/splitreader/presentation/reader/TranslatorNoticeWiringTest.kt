package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A reader whose translator was retired (LibreTranslate, v29) is told once, in the reader, which one
 * replaced it; the notice stays until they dismiss it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = PHONE_PORTRAIT)
class TranslatorNoticeWiringTest {

    @get:Rule val composeRule = createComposeRule()

    private val text = "LibreTranslate is no longer supported — switched to Offline HQ."

    private fun show(notice: String?, onDismiss: () -> Unit = {}) {
        val state = ScreenFixtures.readerContentState.copy(portraitHintDismissed = true, translatorNotice = notice)
        composeRule.setContent {
            SplitReaderTheme {
                ReaderContent(
                    state = state,
                    onNavigateBack = {},
                    onSelectChapter = {},
                    onSetTargetLanguage = {},
                    onAppearanceChange = {},
                    onDismissPortraitHint = {},
                    onReadingPositionChanged = {},
                    onMarkFinished = {},
                    onToggleBookmark = {},
                    onRemoveBookmark = { _, _ -> },
                    onJumpToBookmark = { _, _ -> },
                    onConsumePendingJump = {},
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
                    onDismissTranslatorNotice = onDismiss,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the notice is shown and its button dismisses it`() {
        var dismissed = 0
        show(text) { dismissed++ }

        composeRule.onNodeWithText(text).assertExists()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun `without a notice nothing is shown`() {
        show(null)
        composeRule.onNodeWithText("no longer supported", substring = true).assertDoesNotExist()
    }
}
