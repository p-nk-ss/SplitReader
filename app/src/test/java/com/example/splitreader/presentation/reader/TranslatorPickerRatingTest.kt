package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslatorConfigState
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The free translators show one overall rating: the mean of the translation eval 2026-11-25
 * (46 directions, 1–5) — Quick Translate 3.8, Offline HQ 2.4, ML Kit 1.6 — in half stars.
 * The paid ones were never evaluated, so they show none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = PHONE_PORTRAIT)
class TranslatorPickerRatingTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `free translators show their overall rating, paid ones none`() {
        composeRule.setContent {
            SplitReaderTheme {
                TranslatorPickerDialog(
                    state = TranslatorConfigState(current = TranslationProvider.BERGAMOT, configs = emptyMap()),
                    onSelect = {}, onConfigure = { _, _, _ -> }, onClear = {}, onResetUsage = {}, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Quick Translate (free, online): our rating 4 of 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Offline HQ: our rating 2.5 of 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("ML Kit (offline): our rating 1.5 of 5").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("our rating", substring = true).assertCountEquals(3)
    }
}
