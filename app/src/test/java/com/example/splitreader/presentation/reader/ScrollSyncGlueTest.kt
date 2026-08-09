package com.example.splitreader.presentation.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.MutatePriority
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

        // Index equality alone is satisfied by the glue calling scrollToItem(target.index) with
        // target.index == 12 UNCONDITIONALLY — it would read exactly the same if the translation
        // pane emitted one fewer item per illustration and "item 12" pointed at different content
        // in each pane. `bookItems` keys every item for exactly this reason; assert the keys, not
        // just the index, so a parity break between the two panes' item structure is caught here
        // rather than only by a human comparing screenshots. (Proved to actually catch that: breaking
        // parity by skipping the bottom pane's illustration placeholder failed this assertion —
        // p_0_1[0] vs p_0_1[1] — while leaving the index assertion above green. Evidence is in the
        // phase ledger, .superpowers/sdd/2026-08-04-phase3-portrait-reader/progress.md.)
        assertEquals(
            top.layoutInfo.visibleItemsInfo.first().key,
            bottom.layoutInfo.visibleItemsInfo.first().key,
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
     * round trip. This assertion cannot detect the failure mode its name describes.
     *
     * UPDATE after review round 1. The review judged this negative "a gap, not structural" and
     * named a non-zero-offset target past the 0.95 clamp as a falsifier needing no fixture change.
     * That was **built and measured, and it does not falsify either** — see
     * `the leader keeps an offset past the clamp` below, which stays green with the mask deleted.
     *
     * The limit is NOT "one leader position per action" — an earlier version of this note said
     * that, and it was wrong. Two failure modes were being conflated: the two-scrolls-in-flight
     * race is a separate defect (fixed by single-flighting the collector), whereas the MASK's own
     * purpose needs only ONE position — TOP scrolls, BOTTOM is driven, BOTTOM's induced pulse
     * claims leadership, BOTTOM drives TOP back. This harness does produce one position, so that
     * cannot be why the probe stays green.
     *
     * The likely real reason, and it is a property of production code rather than of Robolectric:
     * `scrollToItem` raises and lowers `isScrollInProgress` inside a single continuation with no
     * suspension point between, while `snapshotFlow` only re-reads its block when the collector
     * resumes. So `onScrollStateChanged(BOTTOM, true)` is probably never delivered at all for an
     * induced scroll — the mask may have nothing to mask on this path, and no offset arithmetic
     * can falsify it.
     *
     * CHEAPEST NEXT STEP if anyone picks this up: make the coordinator injectable (it is
     * `remember { ScrollSyncCoordinator() }` today, unreachable from a test) and assert directly
     * whether `onScrollStateChanged(BOTTOM, true)` is ever delivered during a top-led scroll. One
     * assertion, no frame clock. If it never is, the mask is defence-in-depth for future
     * animated/fling paths rather than load-bearing here, and that should be written down instead
     * of hunted further. Do NOT start from a bigger offset or a gesture harness; that was this
     * note's previous advice and it points at the expensive end.
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

    /**
     * The leader keeps the exact within-item offset it was given, including one past
     * `computeFollowerTarget`'s `0..0.95` clamp. That clamp is lossy and asymmetric — a leader at
     * fraction ~0.99 maps the follower to 0.95 — so this pins that the follower's induced motion
     * does not round-trip back and re-quantise the leader's own offset.
     *
     * **It was added as a falsifier for the missing mask and it is NOT one.** Measured: deleting
     * `beginProgrammaticScroll`/`endProgrammaticScroll` from the leader-driven effect leaves all
     * four tests in this file green (4 tests, 0 failures, twice). The prediction that the clamp's
     * asymmetry would expose an unmasked loop is wrong for this harness, and is recorded here
     * rather than left implied.
     *
     * Why it cannot see it — see the full analysis on
     * `the follower's induced motion does not drag the leader off its target` above. Short version:
     * the reason is NOT "one leader position per action" (an earlier note here said that and it was
     * wrong); the mask's own purpose needs only one position. The likely reason is that
     * `scrollToItem` never suspends between raising and lowering `isScrollInProgress`, so
     * `snapshotFlow` never observes the induced pulse and the coordinator is probably never told
     * the follower moved at all.
     *
     * So the mask's necessity remains argued from the code, not demonstrated by this suite, and the
     * cheap way to settle it is an injectable coordinator — not a bigger offset and not a gesture
     * harness.
     */
    @Test
    fun `the leader keeps an offset past the clamp`() {
        val top = LazyListState()
        val bottom = LazyListState()
        composeVerticalSpread(top, bottom)

        // Land on the item first so its measured height is available.
        composeRule.runOnIdle { runBlocking { top.scrollToItem(20) } }
        composeRule.waitForIdle()
        val itemH = top.layoutInfo.visibleItemsInfo.first { it.index == 20 }.size
        val offset = itemH - 1 // fraction ≈ 0.99, past the 0.95 clamp

        composeRule.runOnIdle { runBlocking { top.scrollToItem(20, offset) } }
        composeRule.waitForIdle()

        assertEquals(20, top.firstVisibleItemIndex)
        assertEquals(
            "The leader was asked for offset $offset within item 20 but ended at " +
                "${top.firstVisibleItemScrollOffset}. The follower was driven to the clamped 0.95 " +
                "fraction and then drove the leader there too — the mask is not holding.",
            offset, top.firstVisibleItemScrollOffset,
        )
    }


    // NOT A TEST, ON PURPOSE — read before adding one back.
    //
    // A competing user-priority scroll on the follower cancels the programmatic one with
    // MutationInterruptedException (a CancellationException). Before commit <this one> that escaped
    // the collector and completed the LaunchedEffect for good — its keys never change while the
    // reader is composed — so scroll sync died silently for the rest of the session. `scrollFollower`
    // in VerticalBookSpread.kt now absorbs exactly that cancellation and rethrows any other.
    //
    // A test for it was written and DELETED: it passed identically with and without the guard
    // (5 tests, 0 failures both ways), i.e. it never provoked the race and would have read as
    // coverage while proving nothing. The reason looks structural rather than a lack of effort —
    // `LazyListState.scrollToItem` runs synchronously inside a single continuation once resumed
    // (raising and lowering isScrollInProgress with no suspension point between), so on
    // Robolectric's single-threaded dispatcher there is no window for a competing scroll to
    // interleave. Provoking it appears to need real concurrency: a UI thread plus actual input.
    //
    // So this is verified against the Compose Foundation 1.7.3 sources and on a device, not here.
    // See the phase's device checklist, "sync survives a finger on the other pane".

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
