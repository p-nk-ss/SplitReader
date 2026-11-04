package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tap anywhere on the page brings up the bars. With the translation off — and every book in an
 * unsupported language reads that way, its language menu reachable only through the bars — only a
 * tap on paragraph text did; a chapter title or the page margin did nothing (found on a tablet
 * emulator with a Catalan book whose first screens are titles and short lines, 2026-11-04).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-xhdpi")
class PageTapTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "The first chapter", paragraphs = listOf("Short line.", "Another one."))),
    )

    private var toggles = 0

    private fun show(showTranslation: Boolean) {
        composeRule.setContent {
            SplitReaderTheme {
                BookSpread(
                    modifier = Modifier.fillMaxSize().testTag("page"),
                    book = book,
                    chapterTranslations = mapOf(0 to listOf("t0", "t1")),
                    showTranslation = showTranslation,
                    showIllustrations = true,
                    splitRatio = 0.5f,
                    horizontalMargin = 32f,
                    style = ScreenFixtures.readerContentState.readingStyle,
                    wordSelection = null,
                    wordHighlightEnabled = true,
                    listState = rememberLazyListState(),
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSelectionDragEnd = {},
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onDismiss = {},
                    onToggleBars = { toggles++ },
                    onSetSplitRatio = {},
                    barsVisible = false,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                )
            }
        }
    }

    @Test
    fun `without the translation, a tap on a chapter title or the margin brings up the bars`() {
        show(showTranslation = false)
        composeRule.onNodeWithText("The first chapter").performClick()
        // The right margin, level with the first paragraph.
        composeRule.onNodeWithTag("page").performTouchInput { click(Offset(right - 10f, centerY)) }
        assertEquals(2, toggles)
    }

    @Test
    fun `a tap on text still toggles once, not twice`() {
        show(showTranslation = false)
        composeRule.onNodeWithText("Short line.").performClick()
        assertEquals(1, toggles)
    }
}
