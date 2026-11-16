package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The language picker's strip said "On-device translation · no cloud calls" whatever the translator
 * was — a false privacy claim with DeepL, Google, Azure or Quick Translate. It now
 * says so only for an on-device translator, and names where the text goes otherwise.
 * (assertExists: the strip sits below the fold of the dialog's scrolled body.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TranslationPrivacyLineTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(provider: TranslationProvider) = composeRule.setContent {
        SplitReaderTheme {
            LanguagePickerDialog(currentTarget = Language.RUSSIAN, translatorProvider = provider, onSelect = {}, onDismiss = {})
        }
    }

    @Test
    fun `an online translator does not claim no cloud calls and says where the text goes`() {
        show(TranslationProvider.DEEPL)
        composeRule.onNodeWithText("no cloud calls", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("On-device translation", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Online translation · text is sent to DeepL").assertExists()
    }

    @Test
    fun `Quick Translate names Google, where its text actually goes`() {
        show(TranslationProvider.QUICK_TRANSLATE)
        composeRule.onNodeWithText("no cloud calls", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Online translation · text is sent to Google").assertExists()
    }

    @Test
    fun `an on-device translator keeps the on-device line`() {
        show(TranslationProvider.BERGAMOT)
        composeRule.onNodeWithText("On-device translation", substring = true).assertExists()
        composeRule.onNodeWithText("no cloud calls").assertExists()
        composeRule.onNodeWithText("text is sent to", substring = true).assertDoesNotExist()
    }

    @Test
    fun `every online translator names who receives the text, and no on-device one does`() {
        TranslationProvider.entries.forEach {
            org.junit.Assert.assertEquals(it.name, it.requiresNetwork, it.sendsTextTo != null)
        }
    }
}
