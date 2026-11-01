package com.example.splitreader.presentation.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The typography sliders had their ranges written in, apart from the clamp in ReadingDefaults: line
 * height offered 1.20-2.00 while the clamp allowed 1.1-2.5. One source now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w600dp-h2000dp")
class SliderRangesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the typography sliders use the ReadingDefaults ranges`() {
        composeRule.setContent {
            SplitReaderTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TypographyControls(appearance = ReadingAppearance.defaults(isTablet = false), onChange = {})
                }
            }
        }
        val ranges = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.ProgressBarRangeInfo].range }
        assertEquals(
            listOf(
                ReadingDefaults.TEXT_SIZE_RANGE,
                ReadingDefaults.LINE_HEIGHT_RANGE,
                ReadingDefaults.PARAGRAPH_SPACING_RANGE,
                ReadingDefaults.LETTER_SPACING_RANGE,
                ReadingDefaults.TEXT_INDENT_RANGE,
            ),
            ranges,
        )
    }

    @Test
    fun `line height keeps the range readers have had`() {
        assertEquals(1.2f..2.0f, ReadingDefaults.LINE_HEIGHT_RANGE)
    }
}
