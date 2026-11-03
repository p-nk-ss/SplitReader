package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.splitreader.domain.model.Chapter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The translation banner floats over the top of the reader. At the start of a book it covered the
 * first chapter's number ("— 1 —"), found on tablet emulators (2026-11-03). Text has to be really
 * measured for this, hence NATIVE graphics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-xhdpi")
class BookStartBannerTest {

    @get:Rule val compose = createComposeRule()

    private fun chapter() = Chapter(index = 0, title = "Alice's Adventures in Wonderland", paragraphs = listOf("Text"))

    private fun bannerBottomAndNumberTop(chapterIndex: Int): Pair<Float, Float> {
        val label = "Preparing translation… 99%"
        compose.setContent {
            Box(Modifier.fillMaxWidth()) {
                ChapterMasthead(chapter = chapter(), chapterIndex = chapterIndex, horizontalMargin = 32f)
                TranslationBanner(label = label, progress = 50, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
        val banner = compose.onNodeWithText(label).getBoundsInRoot()
        val number = compose.onNodeWithText("— ${chapterIndex + 1} —").getBoundsInRoot()
        return banner.bottom.value to number.top.value
    }

    @Test
    fun `the first chapter's number starts below the translation banner`() {
        val (bannerBottom, numberTop) = bannerBottomAndNumberTop(chapterIndex = 0)
        assertTrue(
            "The banner's label ends at ${bannerBottom}dp but the first chapter number starts at " +
                "${numberTop}dp: the banner covers it at the start of the book.",
            bannerBottom + BANNER_CHROME_BELOW_LABEL <= numberTop,
        )
    }
}

/**
 * What the banner draws under its label, which the label's bounds exclude: the progress bar
 * (5dp gap + 3dp bar), the 6dp padding and the 1dp border.
 */
private const val BANNER_CHROME_BELOW_LABEL = 5f + 3f + 6f + 1f
