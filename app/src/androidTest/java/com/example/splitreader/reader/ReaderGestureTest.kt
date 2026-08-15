package com.example.splitreader.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.WindowCompat
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.reader.ReadingStyle
import com.example.splitreader.presentation.reader.VERTICAL_BOTTOM_PANE
import com.example.splitreader.presentation.reader.VERTICAL_TOP_PANE
import com.example.splitreader.presentation.reader.VerticalBookSpread
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule

/**
 * Base for the instrumented reader tests — the ones that need a **real** Android runtime.
 *
 * Everything here exists because Robolectric structurally cannot provide it: a real UI thread, real
 * touch input producing a stream of positions, real concurrency between a gesture and a programmatic
 * scroll, and real window insets. See the phase-3 spec §2 for why each of those defeated the JVM
 * suite.
 *
 * Deliberately does NOT use `MainActivity`: that is `@AndroidEntryPoint`, which would drag Hilt test
 * infrastructure (a custom runner and `HiltTestApplication`) into a suite that needs none of it. The
 * `ComponentActivity` from `ui-test-manifest` is enough — but it fits system windows by default, so
 * [composeSpread] replicates `MainActivity`'s one edge-to-edge line before setting content.
 */
abstract class ReaderGestureTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected val topPane get() = composeRule.onNodeWithTag(VERTICAL_TOP_PANE)
    protected val bottomPane get() = composeRule.onNodeWithTag(VERTICAL_BOTTOM_PANE)

    /**
     * Composes the stacked spread edge-to-edge with hoisted list states, so a test can read the
     * panes' positions directly rather than through semantics.
     */
    protected fun composeSpread(top: LazyListState, bottom: LazyListState) {
        composeRule.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        }
        composeRule.setContent {
            SplitReaderTheme {
                VerticalBookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = fixtureBook,
                    chapterTranslations = fixtureTranslations,
                    showIllustrations = false,
                    verticalSplitRatio = 0.5f,
                    style = ReadingStyle(),
                    wordSelection = null,
                    wordHighlightEnabled = false,
                    listState = top,
                    translationListState = bottom,
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onDismiss = {},
                    onToggleBars = {},
                    onSetVerticalSplitRatio = {},
                    barsVisible = false,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Blocks until neither pane is physically scrolling AND two samples a frame apart agree.
     *
     * One sample is not enough: it can land mid-settle, and a test built on it flickers and then
     * gets "fixed" by raising a timeout rather than by understanding. Returns the settled position
     * of both panes.
     */
    protected fun awaitSettled(top: LazyListState, bottom: LazyListState): Settled {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !top.isScrollInProgress && !bottom.isScrollInProgress
        }
        // Two agreeing samples, taken across real elapsed time — NOT via `mainClock`. On an
        // instrumented run the clock auto-advances against the device's real frame pump, and
        // driving it by hand from the test either throws or silently does nothing. This is one of
        // the few places where the JVM idiom does not transfer.
        var first = snapshot(top, bottom)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Thread.sleep(32L) // ~2 frames at 60Hz
            composeRule.waitForIdle()
            val second = snapshot(top, bottom)
            val agreed = second == first
            first = second
            agreed
        }
        return first
    }

    private fun snapshot(top: LazyListState, bottom: LazyListState) = Settled(
        topIndex = top.firstVisibleItemIndex,
        topOffset = top.firstVisibleItemScrollOffset,
        bottomIndex = bottom.firstVisibleItemIndex,
        bottomOffset = bottom.firstVisibleItemScrollOffset,
    )

    data class Settled(
        val topIndex: Int,
        val topOffset: Int,
        val bottomIndex: Int,
        val bottomOffset: Int,
    )

    /**
     * Fails loudly if the device is not in the state the test's name assumes.
     *
     * Emulator state survives between runs — orientation, font scale and navigation mode all
     * persist — so a test run in the wrong configuration would otherwise pass while exercising a
     * different arm entirely. Phase 2d shipped that exact failure twice, with two tests named
     * "full rail" silently running the compact arm.
     */
    protected fun assertPortraitStacked() {
        val config = composeRule.activity.resources.configuration
        assertTrue(
            "This test needs the stacked arm, i.e. a window narrower than 600dp, but the window is " +
                "${config.screenWidthDp}dp wide. Is the device in landscape? " +
                "`adb shell settings put system user_rotation 0` and disable auto-rotate.",
            config.screenWidthDp < 600,
        )
    }

    protected val fixtureBook = Book(
        title = "Fixture",
        author = "Test",
        filePath = "/fixture",
        chapters = listOf(
            Chapter(
                index = 0,
                title = "Chapter One",
                paragraphs = (0 until 60).map { "Original paragraph $it." },
            ),
        ),
    )

    protected val fixtureTranslations: Map<Int, List<String>> =
        mapOf(0 to (0 until 60).map { "Translated paragraph $it." })
}

/**
 * The harness proving itself, on a real device — not one of the four gesture/inset/rotation
 * invariants those tests cover. If this fails, nothing built on top of [ReaderGestureTest] means
 * anything.
 *
 * A `@Test` declared inside [ReaderGestureTest] itself would never run: JUnit's instrumented
 * runner discovers tests by instantiating the class, and it does not instantiate an abstract one.
 * A tiny concrete subclass is what actually executes it.
 */
class ReaderGestureHarnessSmokeTest : ReaderGestureTest() {

    // Not part of the four invariants — this is the harness proving itself. If it fails, nothing
    // below it means anything.
    //
    // Named without spaces (not a backtick display name): this module's minSdk is 26, so D8 emits
    // a DEX file below version 040, which rejects a SimpleName containing spaces at
    // dexBuilderDebugAndroidTest time — a build failure, not a test failure, and one that hits
    // every test in the class, not just this one. Confirmed against this project's config.
    @org.junit.Test
    fun harnessComposesTheStackedSpreadOnARealDevice() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        topPane.assertExists()
        bottomPane.assertExists()
    }
}
