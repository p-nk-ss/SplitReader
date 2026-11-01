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
                Box(Modifier.width(400.dp).testTag("page")) { Illustration(file.path, horizontalMargin = margin) }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(dpToPx(margin), leftOf(composeRule.onNodeWithTag(ILLUSTRATION_TAG)), 1f)
    }

    private fun png(width: Int, height: Int): File =
        File.createTempFile("img", ".png").apply {
            deleteOnExit()
            outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

    private fun showIllustration(file: File) {
        composeRule.setContent {
            SplitReaderTheme {
                Box(Modifier.width(400.dp).testTag("page")) { Illustration(file.path, horizontalMargin = margin) }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ILLUSTRATION_TAG)).fetchSemanticsNodes().isNotEmpty()
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
        val bounds = composeRule.onNodeWithTag(ILLUSTRATION_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(dpToPx(100f), bounds.width, 1f)
        assertEquals(dpToPx(50f), bounds.height, 1f)
    }

    /** A guard: wide pictures already fitted before the one-dp-per-pixel change, and must still. */
    @Test
    @Config(qualifiers = "w800dp-h600dp-xhdpi")
    fun `a picture wider than the page fits between the margins, keeping its shape`() {
        showIllustration(png(2000, 1000))
        val bounds = composeRule.onNodeWithTag(ILLUSTRATION_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(dpToPx(400f - 2 * margin), bounds.width, 1f)
        assertEquals(bounds.width / 2, bounds.height, 1f)
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
