package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import com.example.splitreader.domain.model.ProviderConfig
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslatorConfigState
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The provider card says what is set up: a LibreTranslate server of the reader's own needs no key,
 * so the card names the server instead of claiming "KEY CONFIGURED".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h2000dp-xxhdpi")
class TranslatorCardStatusTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(libre: ProviderConfig) = composeRule.setContent {
        SplitReaderTheme {
            TranslatorPickerDialog(
                state = TranslatorConfigState(
                    current = TranslationProvider.MLKIT,
                    configs = TranslationProvider.entries.associateWith { ProviderConfig(configured = true) } +
                        (TranslationProvider.LIBRE_TRANSLATE to libre),
                ),
                onSelect = {}, onConfigure = { _, _, _ -> }, onClear = {}, onResetUsage = {}, onDismiss = {},
            )
        }
    }

    /** LibreTranslate's own card (a clickable row merges its texts), not another provider's "KEY CONFIGURED". */
    private fun libreCard(status: String) = hasText("LibreTranslate") and hasText(status)

    @Test
    fun `a own server without a key is named, not called a key`() {
        show(ProviderConfig(configured = true, secondaryValue = "https://libre.example.org", keyStored = false))
        composeRule.onNode(libreCard("SERVER · LIBRE.EXAMPLE.ORG")).assertExists()
        composeRule.onNode(libreCard("KEY CONFIGURED")).assertDoesNotExist()
    }

    @Test
    fun `a stored key still reads as configured`() {
        show(ProviderConfig(configured = true, secondaryValue = "https://libre.example.org", keyStored = true))
        composeRule.onNode(libreCard("KEY CONFIGURED")).assertExists()
        composeRule.onNodeWithText("SERVER · LIBRE.EXAMPLE.ORG").assertDoesNotExist()
    }
}
