package com.example.splitreader.insets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.reader.ReadingStyle
import com.example.splitreader.presentation.reader.VERTICAL_BACKGROUND
import com.example.splitreader.presentation.reader.VERTICAL_BOTTOM_PANE
import com.example.splitreader.presentation.reader.VERTICAL_SPREAD_ROOT
import com.example.splitreader.presentation.reader.VERTICAL_TOP_PANE
import androidx.compose.runtime.Composable
import com.example.splitreader.presentation.navigation.READER_ROUTE
import com.example.splitreader.presentation.reader.VerticalBookSpread
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Task 8: the stacked reader's own insets — the handover Phase 2d's spec §3.5 left in as many
 * words: *"Собственные вставки ридера (`ReaderPane.kt`, `ReaderDialogs.kt`) не входят — их
 * перестраивает фаза 3."* [VerticalBookSpread] draws edge-to-edge and, before this file, consumed
 * none of its own top/bottom insets — only [TranslationBubble]'s `navigationBarsPadding()` did, and
 * that only covers the word-selection popup, not the panes themselves.
 *
 * Reuses [ShellInsetTest]'s dispatch pattern (dispatch straight at the AndroidComposeView, then
 * `waitForIdle()` twice) rather than its `composeShell`, which composes [AppShell] specifically;
 * this file composes [VerticalBookSpread] directly instead.
 */
@Config(qualifiers = PHONE_PORTRAIT)
class VerticalReaderInsetTest : ShellInsetTest() {

    /**
     * The pane must be padded for the status bar EXACTLY ONCE, with the reader hosted by the shell
     * the way it actually ships.
     *
     * The other tests in this file compose [VerticalBookSpread] standalone, and that is precisely
     * why they missed this: every one of `AppShell`'s three reader arms already spends the top
     * inset on a bare `Spacer`, and `windowInsetsPadding` publishes its consumption only to
     * DESCENDANTS — a sibling spacer consumes nothing on the content Box's behalf. So the pane's
     * own `windowInsetsPadding(statusBars)` applied it a second time and opened a blank band above
     * the first line of text, in the main reading view, invisible to this file and to the golden
     * suite alike (the goldens dispatch no insets at all, so every inset query there is 0).
     *
     * Fixed by `consumeWindowInsets(Top)` on the content Box in all three arms, which is what makes
     * the pane's padding self-cancel under the shell while still applying if the reader is ever
     * hosted without one.
     */
    @Test
    fun `the top inset is spent once, not twice, under the shell`() {
        val statusPx = 63
        composeShell(insets(statusBars = statusPx), currentRoute = READER_ROUTE) {
            verticalSpread()
        }

        val pane = boundsOf(VERTICAL_TOP_PANE)

        assertTrue(
            "The top pane starts at ${pane.top} but the status inset is only ${statusPx}px. " +
                "Anything at or beyond ${2 * statusPx} means the inset was consumed twice — once " +
                "by the shell's reader spacer and again by the pane itself.",
            pane.top < 2 * statusPx.toFloat(),
        )
        assertTrue(
            "The top pane starts at ${pane.top}, above the ${statusPx}px status inset — the text " +
                "would run under the system clock.",
            pane.top >= statusPx.toFloat() - 1f,
        )
    }

    /**
     * Composes [VerticalBookSpread] alone (no [AppShell]) and delivers [dispatched] to it, using
     * the same dispatch target and double-`waitForIdle` established by [ShellInsetTest.composeShell]
     * — see that function's KDoc for why both are required.
     */
    /**
     * The background must still reach every edge — the half of Phase 2b's defect #4 that the pane
     * assertions cannot see.
     *
     * Those assert pane-vs-root bounds only. `VERTICAL_SPREAD_ROOT` is tagged outermost, so it
     * reports the full window whether the padding sits on the panes or on the root; and the panes
     * report the inset positions either way. Move the padding up to the root and both still pass,
     * while the painted background shrinks inward and leaves the bare band 2b spent a fix on. This
     * is the assertion that distinguishes the two.
     */
    @Test
    fun `the background reaches the edges even though the content does not`() {
        composeVerticalReader(insets(statusBars = 63, navigationBars = 48))

        val background = boundsOf(VERTICAL_BACKGROUND)
        val root = boundsOf(VERTICAL_SPREAD_ROOT)

        assertTrue(
            "The background spans ${background.top}..${background.bottom} but the spread spans " +
                "${root.top}..${root.bottom}. It must reach both edges — an inset applied at an " +
                "ancestor instead of at the panes shrinks it and leaves a bare band.",
            background.top <= root.top + 1f && background.bottom >= root.bottom - 1f,
        )
    }

    /** The spread with fixture content — shared by the standalone and shell-hosted paths. */
    @Composable
    private fun verticalSpread() {
        VerticalBookSpread(
            modifier = Modifier.fillMaxSize(),
            book = fixtureBook,
            chapterTranslations = fixtureTranslations,
            showIllustrations = false,
            verticalSplitRatio = 0.5f,
            style = ReadingStyle(),
            wordSelection = null,
            wordHighlightEnabled = false,
            listState = LazyListState(),
            translationListState = LazyListState(),
            onWordSelected = { _, _, _, _, _ -> },
            onSelectionDragged = { _, _ -> },
            onSaveWord = { _, _, _ -> },
            onSpeak = { _, _ -> },
            onDismiss = {},
            onToggleBars = {},
            onSetVerticalSplitRatio = {},
            barsVisible = true,
            sourceLang = Language.ENGLISH,
            targetLang = Language.RUSSIAN,
        )
    }

    private fun composeVerticalReader(dispatched: WindowInsetsCompat) {
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
                    listState = LazyListState(),
                    translationListState = LazyListState(),
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
                    onDismiss = {},
                    onToggleBars = {},
                    onSetVerticalSplitRatio = {},
                    barsVisible = true,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                )
            }
        }
        composeRule.waitForIdle()

        val composeView = composeRule.activity
            .findViewById<android.view.ViewGroup>(android.R.id.content)
            .getChildAt(0)
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(composeView, dispatched) }
        composeRule.waitForIdle()
    }

    /**
     * The bottom pane's last line must clear the gesture bar. The reader draws edge-to-edge, so
     * without an explicit bottom inset the final line of the translation sits under the system
     * navigation and is unreadable exactly where the eye ends up.
     */
    @Test
    fun `the bottom pane clears the navigation bar`() {
        val navPx = 48
        composeVerticalReader(insets(statusBars = 63, navigationBars = navPx))

        val pane = boundsOf(VERTICAL_BOTTOM_PANE)
        val spread = boundsOf(VERTICAL_SPREAD_ROOT)

        assertTrue(
            "The bottom pane ends at ${pane.bottom} and the spread at ${spread.bottom}; it must stop " +
                "at least the ${navPx}px gesture inset short of it, or the last line of the " +
                "translation is under the navigation bar.",
            pane.bottom <= spread.bottom - navPx + 1f,
        )
    }

    /**
     * Mirrors [TopInsetInvariantTest]'s shape for the top pane: content must not start above the
     * status-bar inset. The reader route's own top slot is a bare inset spacer with no status
     * strip of its own (see [TopInsetInvariantTest]'s KDoc on why per-arm chrome floors matter) —
     * [VerticalBookSpread] is composed here without a shell around it at all, so the floor is the
     * dispatched inset itself, not a shared literal.
     */
    @Test
    fun `the top pane clears the status bar`() {
        val statusPx = 63
        composeVerticalReader(insets(statusBars = statusPx, navigationBars = 48))

        val pane = boundsOf(VERTICAL_TOP_PANE)
        val spread = boundsOf(VERTICAL_SPREAD_ROOT)

        assertTrue(
            "The top pane starts at ${pane.top} and the spread at ${spread.top}; it must start at " +
                "least the ${statusPx}px status-bar inset below it, or the first line of the " +
                "original is under the status bar.",
            pane.top >= spread.top + statusPx - 1f,
        )
    }

    companion object {
        private const val PARAGRAPH_COUNT = 20

        private val fixtureBook = Book(
            title = "Fixture",
            author = "Test",
            filePath = "/fixture",
            chapters = listOf(
                Chapter(
                    index = 0,
                    title = "Chapter One",
                    paragraphs = (0 until PARAGRAPH_COUNT).map { "Original paragraph $it." },
                    images = emptyList(),
                ),
            ),
        )

        private val fixtureTranslations: Map<Int, List<String>> = mapOf(
            0 to (0 until PARAGRAPH_COUNT).map { "Translated paragraph $it." },
        )
    }
}
