package com.example.splitreader.presentation.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.presentation.theme.SplitReaderTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reader's bars animate in and out of a Column above and below the list, so the panes' height
 * changes frame by frame on every tap. A picture capped by that height zoomed and pushed the text
 * below it about (found in review). The cap comes from the whole reader area instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IllustrationStabilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the pane's height changing, as the bars animate, leaves a tall picture's size alone`() {
        val plate = File.createTempFile("plate", ".png").apply {
            deleteOnExit()
            outputStream().use { Bitmap.createBitmap(300, 2000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val book = Book(
            title = "T", author = "A", filePath = "/t",
            chapters = listOf(Chapter(index = 0, title = "One", paragraphs = listOf("p0", "p1"), images = listOf(ChapterImage(0, plate.path)))),
        )
        var areaHeight by mutableStateOf(800.dp)
        composeRule.setPictureContent {
            SplitReaderTheme {
                Box(Modifier.fillMaxWidth().height(areaHeight)) {
                    VerticalBookSpread(
                        modifier = Modifier.fillMaxSize(),
                        book = book,
                        chapterTranslations = mapOf(0 to listOf("t0", "t1")),
                        showIllustrations = true,
                        verticalSplitRatio = 0.5f,
                        horizontalMargin = ReadingDefaults.HORIZONTAL_MARGIN,
                        style = ReadingStyle(),
                        wordSelection = null,
                        wordHighlightEnabled = false,
                        listState = LazyListState(),
                        translationListState = LazyListState(),
                        onWordSelected = { _, _, _, _, _ -> },
                        onSelectionDragged = { _, _ -> },
                        onSelectionDragEnd = {},
                        onSaveWord = { _, _, _ -> },
                        onSpeak = { _, _ -> },
                        onDismiss = {},
                        onToggleBars = {},
                        onSetVerticalSplitRatio = {},
                        barsVisible = true,
                        sourceLang = Language.ENGLISH,
                        targetLang = Language.RUSSIAN,
                        illustrationMaxHeight = illustrationMaxHeight(900.dp, 0.5f),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // size, not boundsInRoot: the visible part is clipped by the pane, which does shrink.
        val before = composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true).fetchSemanticsNode().size

        areaHeight = 700.dp // the bars took 100 dp
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true).fetchSemanticsNode().size

        assertEquals(before, after)
    }

    @Test
    fun `the cap is the pane's share of the reading height, less the picture's padding, at least 120 dp`() {
        assertEquals(868.dp, illustrationMaxHeight(900.dp))
        assertEquals(418.dp, illustrationMaxHeight(900.dp, 0.5f))
        assertEquals(120.dp, illustrationMaxHeight(200.dp, 0.5f))
        assertEquals(androidx.compose.ui.unit.Dp.Infinity, illustrationMaxHeight(androidx.compose.ui.unit.Dp.Infinity))
    }
}
