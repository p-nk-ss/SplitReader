package com.example.splitreader.insets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.height
import com.example.splitreader.presentation.navigation.ShellTestTags
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import androidx.compose.foundation.layout.Box

/**
 * I5: with the keyboard up, the content's bottom edge sits ON the keyboard, not above it.
 *
 * The defect this pins: the compact arm padded CONTENT by the full IME height, but the bottom bar
 * still sat below CONTENT under the keyboard. Compose insets are measured from the window edge,
 * not from the padded node, so CONTENT ended one bar-height (64dp + gesture inset) above the
 * keyboard and the shell background showed through as an empty band in the app's colour — seen on
 * every search field (Library, Catalog, Words).
 */
class ImeInsetInvariantTest : ShellInsetTest() {

    private val imePx = 800
    private val navBarPx = 63

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact arm content ends exactly at the keyboard top`() {
        composeShell(insets(navigationBars = navBarPx, ime = imePx)) {
            Box(Modifier.fillMaxSize().testTag(INNER))
        }
        val rootBottom = with(composeRule.density) {
            composeRule.onRoot().getBoundsInRoot().height.toPx()
        }
        val innerBottom = boundsOf(INNER).bottom
        assertEquals(
            "Content ends at ${innerBottom}px but the keyboard starts at ${rootBottom - imePx}px " +
                "(window ${rootBottom}px, IME ${imePx}px). The gap is the shell background showing " +
                "between the content and the keyboard.",
            rootBottom - imePx,
            innerBottom,
            1f,
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact arm hides the bottom bar under the keyboard and shows it otherwise`() {
        composeShell(insets(navigationBars = navBarPx, ime = imePx))
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `compact arm keeps the bottom bar when no keyboard is up`() {
        composeShell(insets(navigationBars = navBarPx))
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
    }

    private companion object {
        const val INNER = "test:inner"
    }
}
