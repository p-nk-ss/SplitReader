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
 * The picker's model line is only as fresh as the check `ReaderContent` asks for when the picker
 * opens; the ViewModel and dialog tests pass even with that call gone (v29 review). Opened the way a
 * reader does — the language chip in the top bar — it asks once, and shows the answer it is given.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = PHONE_PORTRAIT)
class LanguagePickerWiringTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `opening the language picker checks the model once and shows its state`() {
        var checks = 0
        val state = ScreenFixtures.readerContentState.copy(
            portraitHintDismissed = true,
            modelStatus = ModelStatus(TranslationProvider.BERGAMOT, downloaded = true),
        )
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
                    onCheckModel = { checks++ },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("nothing asked before the picker opens", 0, checks)

        composeRule.onNodeWithText("→").performClick() // the top bar's language chip
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LANGUAGE_TAB_TARGET).assertExists()
        assertEquals(1, checks)
        composeRule.onNodeWithText("OFFLINE HQ · DOWNLOADED").assertExists()
    }
}
