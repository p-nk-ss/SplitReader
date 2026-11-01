package com.example.splitreader.presentation.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.splitreader.domain.model.Chapter
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
 * The page-margin setting moved the text but not illustrations or chapter titles: both had a fixed
 * 32 dp. They now use the reader's margin, so pictures line up with the text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageMarginsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val margin = 12f
    private var maxHeight = Dp.Infinity

    private fun leftOf(node: androidx.compose.ui.test.SemanticsNodeInteraction): Float {
        val page = composeRule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.left
        return node.fetchSemanticsNode().boundsInRoot.left - page
    }

    private fun dpToPx(dp: Float) = dp * composeRule.activity.resources.displayMetrics.density

    @Test
    fun `an illustration starts at the page margin`() {
        // Wider than the page, so the picture fills the width between the margins.
        val file = File.createTempFile("plate", ".png").apply { deleteOnExit() }
        file.outputStream().use { Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }

        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.width(400.dp).testTag("page")) { Illustration(file.path, horizontalMargin = margin, maxHeight = maxHeight) }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(dpToPx(margin), leftOf(composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true)), 1f)
    }

    private fun png(width: Int, height: Int): File =
        File.createTempFile("img", ".png").apply {
            deleteOnExit()
            outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

    private fun showIllustration(file: File) {
        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.width(400.dp).testTag("page")) { Illustration(file.path, horizontalMargin = margin, maxHeight = maxHeight) }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Found on a device: a plate drawn at its bitmap size came out half size on a tablet (density 2)
     * and smaller still on a phone. One image pixel is one dp, as browsers and e-readers size it.
     */
    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `a small picture is one dp per image pixel, not one screen pixel`() {
        showIllustration(png(100, 50))
        val bounds = composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(dpToPx(100f), bounds.width, 1f)
        assertEquals(dpToPx(50f), bounds.height, 1f)
    }

    /** A guard: wide pictures already fitted before the one-dp-per-pixel change, and must still. */
    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `a picture wider than the page fits between the margins, keeping its shape`() {
        showIllustration(png(2000, 1000))
        val bounds = composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(dpToPx(400f - 2 * margin), bounds.width, 1f)
        assertEquals(bounds.width / 2, bounds.height, 1f)
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `a very large picture, decoded smaller, is still sized by the file's own pixels`() {
        // 4000 px is over twice this screen's width, so the bitmap is subsampled to 2000 px.
        showIllustration(png(4000, 100))
        val bounds = composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(dpToPx(400f - 2 * margin), bounds.width, 1f)
        assertEquals(dpToPx((400f - 2 * margin) / 40f), bounds.height, 1f)
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `a portrait plate taller than the pane fits its height, keeping its shape`() {
        maxHeight = 300.dp
        showIllustration(png(300, 600))
        val bounds = composeRule.onNodeWithTag(ILLUSTRATION_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(dpToPx(300f), bounds.height, 1f)
        assertEquals(dpToPx(150f), bounds.width, 1f)
    }

    /**
     * The stacked layout scrolls its two panes in step by row. The translation pane showed a 24 dp
     * gap for each picture, so a tall plate in the original pane swept by while the other crawled.
     */
    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `the translation pane's space for a picture is as tall as the picture's row`() {
        val file = png(300, 200)
        composeRule.setContent {
            SplitReaderTheme {
                androidx.compose.foundation.layout.Row {
                    Box(Modifier.width(400.dp).testTag("original")) { Illustration(file.path, margin, Dp.Infinity) }
                    Box(Modifier.width(400.dp).testTag("translation")) { IllustrationSpace(file.path, margin, Dp.Infinity) }
                }
            }
        }
        // Each pane reads the file's size on its own background call; wait for both.
        fun height(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        composeRule.waitUntil(5_000) { height("original") > 0f && height("translation") > 0f }
        val original = height("original")
        val translation = height("translation")
        assertEquals(dpToPx(200f + 32f), original, 1f)
        assertEquals(original, translation, 1f)
    }

    /**
     * Found on a tablet: a picture can now fill the screen, and a tap on it did nothing, so with no
     * text in view there was no way to bring up the reader's bars.
     */
    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `a tap on a picture, or on its space in the translation pane, toggles the bars`() {
        val file = png(300, 200)
        var taps = 0
        composeRule.setContent {
            SplitReaderTheme {
                androidx.compose.foundation.layout.Column {
                    Box(Modifier.width(400.dp)) { Illustration(file.path, margin, Dp.Infinity, onTap = { taps++ }) }
                    Box(Modifier.width(400.dp)) { IllustrationSpace(file.path, margin, Dp.Infinity, onTap = { taps++ }) }
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_ROW_TAG)).fetchSemanticsNodes().size == 2
        }
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_ROW_TAG))[0].performClick()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_ROW_TAG))[1].performClick()
        assertEquals(2, taps)
    }

    @Test
    fun `the size rule - one dp per pixel, within the page and the pane`() {
        assertEquals(DpSize(100.dp, 50.dp), illustrationSize(IntSize(100, 50), 376.dp, Dp.Infinity))
        assertEquals(DpSize(376.dp, 188.dp), illustrationSize(IntSize(2000, 1000), 376.dp, Dp.Infinity))
        assertEquals(DpSize(150.dp, 300.dp), illustrationSize(IntSize(300, 600), 376.dp, 300.dp))
    }

    @Test
    fun `a long chapter title wraps at the page margin`() {
        val title = "A chapter title long enough to wrap across the full width of the page several times over"
        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.width(400.dp).testTag("page")) {
                    ChapterMasthead(Chapter(index = 0, title = title, paragraphs = listOf("p")), 0, horizontalMargin = margin)
                }
            }
        }
        assertEquals(dpToPx(margin), leftOf(composeRule.onNodeWithText(title)), 1f)
    }
}
