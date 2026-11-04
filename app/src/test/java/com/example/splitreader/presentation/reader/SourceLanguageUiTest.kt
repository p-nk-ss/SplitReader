package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SourceDetection
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reader's controls for the book's language (2026-11-04). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h1600dp-xhdpi")
class SourceLanguageUiTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val finnish = ScreenFixtures.readerContentState.copy(sourceDetection = SourceDetection.Unsupported("fi"))

    @Test
    fun `the source tab shows what was detected and picks or clears the book's language`() {
        val picked = mutableListOf<Language?>()
        composeRule.setContent {
            SplitReaderTheme {
                LanguagePickerDialog(
                    currentTarget = Language.RUSSIAN, onSelect = {}, onDismiss = {},
                    sourceDetection = SourceDetection.Unsupported("fi"), chosenSource = null,
                    onSelectSource = { picked += it }, initialTab = LanguagePickerTab.SOURCE,
                )
            }
        }
        composeRule.onNodeWithText("Detected: Finnish — not supported").assertIsDisplayed()
        composeRule.onNodeWithText("Deutsch").performClick()
        composeRule.onNodeWithTag(LANGUAGE_AUTO_TILE).performClick()
        assertEquals(listOf(Language.GERMAN, null), picked)
    }

    @Test
    fun `the target tab still picks the translation's language`() {
        var target: Language? = null
        composeRule.setContent {
            SplitReaderTheme {
                LanguagePickerDialog(currentTarget = Language.RUSSIAN, onSelect = { target = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Automatic").assertDoesNotExist()
        composeRule.onNodeWithText("Deutsch").performClick()
        assertEquals(Language.GERMAN, target)
    }

    @Test
    fun `an untranslated book's footer names its language and claims no translation`() {
        composeRule.setContent { SplitReaderTheme { ReaderStatusFooter(finnish) } }
        composeRule.onNodeWithText("FI → —").assertIsDisplayed()
    }

    @Test
    fun `the banner says which language it is and opens the picker`() {
        var opened = 0
        composeRule.setContent { SplitReaderTheme { UntranslatedBanner(SourceDetection.Unsupported("fi"), onChoose = { opened++ }) } }
        composeRule.onNodeWithText("Finnish", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Choose language", ignoreCase = true).performClick()
        assertEquals(1, opened)
    }
}
