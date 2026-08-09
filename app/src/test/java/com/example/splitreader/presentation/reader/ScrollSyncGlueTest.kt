package com.example.splitreader.presentation.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.PHONE_PORTRAIT
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Proves the Compose glue in [VerticalBookSpread] actually drives the follower pane from the
 * leader — the part [ScrollSyncCoordinator] and [computeFollowerTarget] cannot prove on their own
 * because they never touch a real `LazyListState`.
 *
 * The fixture book carries [ChapterImage]s so the translation pane's parity items (a placeholder
 * per illustration — see `bookItems`' KDoc) are exercised through an actual scroll, not just
 * asserted to exist by construction.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_PORTRAIT, application = Application::class)
class ScrollSyncGlueTest {

    @get:Rule val composeRule = createComposeRule()

    /**
     * Built in [setUp], not as a `companion object` constant: the two [ChapterImage] paths must
     * point at real, decodable PNGs on disk. `Illustration` (`ReaderPane.kt`) decodes the file
     * asynchronously (`produceState` + `Dispatchers.IO`) and renders nothing at all until that
     * completes — an image item scrolled straight onto by `scrollToItem` (rather than approached by
     * an animated scroll, which composes the items in between) is measured on its first frame
     * before the decode has landed, i.e. at 0px, and a 0px item at the scroll target makes
     * `firstVisibleItemIndex` snap one item past where the test asked it to go. This was observed
     * directly while writing this test: an image anchored at paragraph 10 (item index 12) made
     * `top.scrollToItem(12)` settle at 13, not 12, deterministically. The fix is placement, not a
     * workaround: both images below sit where a `scrollToItem(9|12|20)` jump — and the resulting
     * viewport — never lands on or near them, so they are always fully composed (and thus decoded,
     * settled during the initial [composeVerticalSpread]'s `waitForIdle`) or fully outside every
     * viewport this file scrolls to. See `visibleItemsInfo` dumped while diagnosing this for the
     * exact item sizes.
     */
    private lateinit var fixtureBook: Book

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fixtureBook = Book(
            title = "Fixture",
            author = "Test",
            filePath = "/fixture",
            chapters = listOf(
                Chapter(
                    index = 0,
                    title = "Chapter One",
                    paragraphs = (0 until PARAGRAPH_COUNT).map { "Original paragraph $it." },
                    images = listOf(
                        // item index 1 — inside the initial viewport, so it is fully decoded
                        // during composeVerticalSpread's own waitForIdle, well before any scroll.
                        ChapterImage(anchorParagraph = 0, path = writeFixtureImage(context, "leading.png")),
                        // item index PARAGRAPH_COUNT + 2 — the anchorParagraph == paragraphs.size
                        // ("after the last paragraph") case, placed far past every scroll target
                        // (9, 12, 20) and its resulting viewport used in this file.
                        ChapterImage(
                            anchorParagraph = PARAGRAPH_COUNT,
                            path = writeFixtureImage(context, "trailing.png"),
                        ),
                    ),
                ),
            ),
        )
    }

    /** Writes a small opaque PNG to the test app's cache dir and returns its absolute path. */
    private fun writeFixtureImage(context: Application, name: String): String {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    @Test
    fun `scrolling the top pane moves the bottom pane to the same item`() {
        val top = LazyListState()
        val bottom = LazyListState()
        composeVerticalSpread(top, bottom)

        composeRule.runOnIdle { runBlocking { top.scrollToItem(12) } }
        composeRule.waitForIdle()

        assertEquals(
            "The top pane is at item ${top.firstVisibleItemIndex} but the bottom pane is at " +
                "${bottom.firstVisibleItemIndex} — the panes are not synchronised.",
            12, bottom.firstVisibleItemIndex,
        )
    }

    @Test
    fun `scrolling the bottom pane moves the top pane — either pane may lead`() {
        val top = LazyListState()
        val bottom = LazyListState()
        composeVerticalSpread(top, bottom)

        composeRule.runOnIdle { runBlocking { bottom.scrollToItem(9) } }
        composeRule.waitForIdle()

        assertEquals(9, top.firstVisibleItemIndex)
    }

    /**
     * The follower being moved must not turn round and drive the leader back.
     *
     * FINDING (task-6 Step 6, break 3 — mask pair removed): this assertion did not fail when
     * `beginProgrammaticScroll`/`endProgrammaticScroll` were deleted from `VerticalBookSpread`'s
     * leader-driven effect and the whole feedback loop was left free to run. Reproduced three
     * times, not a fluke. Root cause: `computeFollowerTarget` always returns the leader's own
     * index verbatim (`FollowerTarget.index == leaderFirstIndex`, unconditionally — see
     * `ScrollSync.kt`); a masked or unmasked round trip therefore always computes the SAME target
     * index. This scroll also lands at offset 0 (a bare `scrollToItem(20)`), and `0 / anything`
     * stays 0 through the fraction calculation, so there is no numeric drift in the offset either
     * — a real, unmasked ping-pong would still leave `top.firstVisibleItemIndex == 20` on every
     * round trip. This assertion cannot detect the failure mode its name describes; it would need
     * to watch scroll-event counts or a non-zero-offset target to have a chance of catching it.
     * Left as prescribed by the task-6 brief rather than rewritten, per this project's
     * "report as a finding, don't silently patch the proof" convention.
     */
    @Test
    fun `the follower's induced motion does not drag the leader off its target`() {
        val top = LazyListState()
        val bottom = LazyListState()
        composeVerticalSpread(top, bottom)

        composeRule.runOnIdle { runBlocking { top.scrollToItem(20) } }
        composeRule.waitForIdle()

        assertEquals(
            "The leader ended at ${top.firstVisibleItemIndex} instead of 20 — the follower drove it back.",
            20, top.firstVisibleItemIndex,
        )
    }

    // ── Fixture ──────────────────────────────────────────────────────────

    /**
     * Composes [VerticalBookSpread] with a fixture book of 40 paragraphs and two illustrations
     * (leading, trailing — see [ChapterImage] anchor semantics), a fully populated translation map
     * so no paragraph falls back to the shimmering `TranslationPlaceholder` (an infinite animation
     * that has no place in a deterministic scroll test), and every callback a no-op.
     */
    private fun composeVerticalSpread(top: LazyListState, bottom: LazyListState) {
        composeRule.setContent {
            SplitReaderTheme {
                VerticalBookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = fixtureBook,
                    chapterTranslations = fixtureTranslations,
                    showIllustrations = true,
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
                    barsVisible = true,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                )
            }
        }
        composeRule.waitForIdle()
    }

    companion object {
        /**
         * One chapter, 40 paragraphs, two [ChapterImage]s: leading (anchor 0) and trailing
         * (anchor == paragraphs.size, i.e. after the last paragraph — see [setUp] for why no image
         * sits near the scroll targets this file uses). `bookItems` emits an item for every one of
         * these in both panes — that parity is exactly what a scroll test over an illustrated book
         * proves end to end. The book itself is built per-test in [setUp] because its image paths
         * must point at real files.
         */
        private const val PARAGRAPH_COUNT = 40

        private val fixtureTranslations: Map<Int, List<String>> = mapOf(
            0 to (0 until PARAGRAPH_COUNT).map { "Translated paragraph $it." },
        )
    }
}
