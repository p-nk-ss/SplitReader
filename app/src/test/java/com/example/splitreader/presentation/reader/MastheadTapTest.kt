package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tap on a chapter heading ([ChapterMasthead]) must toggle the bars exactly like a tap on a
 * paragraph — every other row in [BookSpread]/[VerticalBookSpread] wires `onTap` directly; the
 * masthead didn't, so a reader landing on a heading (frequent — it is the first row of every
 * chapter) got no response (E2E on a phone kept landing on headings).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-xhdpi")
class MastheadTapTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "The first chapter", paragraphs = listOf("Short line."))),
    )

    private var toggles = 0

    @Test
    fun `a tap on the masthead in the side-by-side layout toggles the bars`() {
        composeRule.setContent {
            SplitReaderTheme {
                BookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = book,
                    chapterTranslations = mapOf(0 to listOf("t0")),
                    showTranslation = true,
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

        composeRule.onAllNodesWithText("The first chapter")[0].performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `a tap on the masthead toggles the bars in both panes of the stacked layout`() {
        composeRule.setContent {
            SplitReaderTheme {
                VerticalBookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = book,
                    chapterTranslations = mapOf(0 to listOf("t0")),
                    showIllustrations = true,
                    verticalSplitRatio = 0.5f,
                    horizontalMargin = ReadingDefaults.HORIZONTAL_MARGIN,
                    style = ScreenFixtures.readerContentState.readingStyle,
                    wordSelection = null,
                    wordHighlightEnabled = true,
                    listState = LazyListState(),
                    translationListState = LazyListState(),
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSelectionDragEnd = {},
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onDismiss = {},
                    onToggleBars = { toggles++ },
                    onSetVerticalSplitRatio = {},
                    barsVisible = false,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                )
            }
        }

        val mastheads = composeRule.onAllNodesWithText("The first chapter")
        mastheads[0].performClick()
        mastheads[1].performClick()
        assertEquals(2, toggles)
    }
}
