package com.example.splitreader.presentation.reader

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.MotionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * The stacked (portrait/narrow-window) layout: original above, translation below, a draggable
 * horizontal divider between. Used in place of [BookSpread] when
 * `isCompactWidth(maxWidth) && state.showTranslation` — see the branch in `ReaderContent`.
 *
 * There is deliberately no `showTranslation` parameter: the caller only reaches this composable
 * when translation is on, so accepting the flag here would create a second, unreachable
 * "translation off" path.
 *
 * The two [LazyColumn]s are kept in step by a [ScrollSyncCoordinator]: whichever pane is
 * physically being scrolled leads, and the other is driven to the same item via
 * [computeFollowerTarget] (proportional inside the item, exact at its boundaries — see that
 * function's KDoc). Both panes emit through the same [bookItems] structure, so their item indices
 * are identical by construction; the coordinator only has to carry an index and an offset across,
 * never translate one pane's structure into the other's.
 */
@Composable
internal fun VerticalBookSpread(
    modifier: Modifier,
    book: Book,
    chapterTranslations: Map<Int, List<String>>,
    showIllustrations: Boolean,
    verticalSplitRatio: Float,
    style: ReadingStyle,
    wordSelection: WordSelection?,
    wordHighlightEnabled: Boolean,
    listState: LazyListState,
    translationListState: LazyListState,
    onWordSelected: (word: String, chapterIndex: Int, paragraphIndex: Int, start: Int, end: Int) -> Unit,
    onSelectionDragged: (start: Int, end: Int) -> Unit,
    onSaveWord: (word: String, chapterIndex: Int, paragraphIndex: Int) -> Unit,
    onSpeak: (text: String, langCode: String) -> Unit,
    onDismiss: () -> Unit,
    onToggleBars: () -> Unit,
    onSetVerticalSplitRatio: (Float) -> Unit,
    barsVisible: Boolean,
    sourceLang: Language,
    targetLang: Language,
) {
    var dragRatio by remember { mutableFloatStateOf(Float.NaN) }
    val effectiveRatio = if (dragRatio.isNaN()) verticalSplitRatio else dragRatio
    var paneAreaHeightPx by remember { mutableIntStateOf(0) }
    val palette = LocalReaderPalette.current

    val coordinator = remember { ScrollSyncCoordinator() }
    // Mirrors coordinator.leader() as Compose-observable state. ScrollSyncCoordinator is
    // deliberately Compose-free (see its KDoc) so its arbitration stays plain-JUnit-testable, but
    // that means `leader` is an ordinary Kotlin var: reading it inside `snapshotFlow` does not
    // register a snapshot subscription. A run of the leader-driven flow below that reads only
    // `coordinator.leader()` and finds it null returns having read no observable state at all, so
    // snapshotFlow has nothing to re-invoke on and the effect goes quiet forever, even once a pane
    // starts scrolling and coordinator.leader() truly changes. Every place that can change
    // leadership publishes the new value here instead, and the flow below reads THIS.
    var leaderPane by remember { mutableStateOf<ScrollSyncCoordinator.Pane?>(null) }

    // Feed physical scroll state into the arbitration.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect {
                coordinator.onScrollStateChanged(ScrollSyncCoordinator.Pane.TOP, it)
                leaderPane = coordinator.leader()
            }
    }
    LaunchedEffect(translationListState) {
        snapshotFlow { translationListState.isScrollInProgress }
            .collect {
                coordinator.onScrollStateChanged(ScrollSyncCoordinator.Pane.BOTTOM, it)
                leaderPane = coordinator.leader()
            }
    }

    // Drive the follower from whichever pane currently leads.
    LaunchedEffect(listState, translationListState) {
        snapshotFlow {
            val lead = leaderPane ?: return@snapshotFlow null
            val leaderState = if (lead == ScrollSyncCoordinator.Pane.TOP) listState else translationListState
            Triple(lead, leaderState.firstVisibleItemIndex, leaderState.firstVisibleItemScrollOffset)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .conflate()
            .collect { (lead, _, _) ->
                // Wait for a real frame boundary before touching the follower's LazyListState —
                // this is what breaks the "measureAndLayout called during measure layout"
                // reentrancy (both panes share one AndroidComposeView; forceRemeasure() on the
                // follower while the leader's own forceRemeasure() is still on the call stack is
                // illegal). This MUST happen inside the collector itself, not inside a separate
                // `scope.launch` (an earlier version of this code did that): a `launch` returns
                // immediately without suspending the collector, so `conflate()` above never gets a
                // chance to do its job — every distinct leader position spawns its own coroutine,
                // and two of those racing UNMASK each other mid-flight (the newer's
                // beginProgrammaticScroll() runs, the older's cancelled scrollToItem() unwinds
                // into its finally block, which clears the mask the newer one is still relying on)
                // — reinstating the exact feedback loop the mask exists to prevent. Suspending
                // right here instead means the collector is unavailable while waiting, so
                // `conflate()` collapses any backlog to one value and there is only ever one
                // follower scroll in flight, with one owner of the mask.
                withFrameNanos {}

                // Re-read the leader's live position rather than trusting the tuple destructured
                // above: a frame has passed since it was captured, and driving the follower to a
                // now-stale (index, offset) would apply a target that's already behind the leader.
                // `followerPane` comes from `lead`, not `coordinator.follower()`: the three
                // isScrollInProgress-feeding collectors above run independently with no ordering
                // guarantee against this one, so a live re-query can race the gesture's own settle
                // (leader already cleared → follower() null → the tail of the gesture dropped).
                val leaderState = if (lead == ScrollSyncCoordinator.Pane.TOP) listState else translationListState
                val followerPane =
                    if (lead == ScrollSyncCoordinator.Pane.TOP) ScrollSyncCoordinator.Pane.BOTTOM
                    else ScrollSyncCoordinator.Pane.TOP
                val followerState =
                    if (followerPane == ScrollSyncCoordinator.Pane.TOP) listState else translationListState

                val index = leaderState.firstVisibleItemIndex
                val offset = leaderState.firstVisibleItemScrollOffset

                val leaderItemH = leaderState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == index }?.size ?: 0
                val followerItemH = followerState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == index }?.size

                val target = computeFollowerTarget(index, offset, leaderItemH, followerItemH)

                // Skip-if-already-there: without this every correction re-triggers the flow.
                if (followerState.firstVisibleItemIndex == target.index &&
                    followerState.firstVisibleItemScrollOffset == target.offsetPx
                ) return@collect

                scrollFollower(coordinator, followerPane, followerState, target)
            }
    }

    // At-rest alignment: drives BOTTOM to match TOP when nothing is physically scrolling, for the
    // two moments the leader-driven effect above can't reach (it only fires while a pane is
    // physically scrolling). TOP→BOTTOM only, deliberately, not symmetric: this codebase already
    // treats the top pane's listState as the single canonical position at rest — scroll restore,
    // progress persistence, bookmark jumps and markFinished all key off it (see ReaderScreen.kt) —
    // so realigning FROM the bottom pane here would fight that source of truth. The leader-driven
    // effect above is what keeps the top pane honest during an actual bottom-led gesture; this one
    // only ever needs to run afterward, once TOP is settled again.
    val alignBottomToTop: suspend () -> Unit = {
        if (coordinator.leader() == null) { // a real gesture owns it otherwise
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val leaderItemH = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }?.size ?: 0
            val followerItemH = translationListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }?.size
            val target = computeFollowerTarget(index, offset, leaderItemH, followerItemH)
            if (translationListState.firstVisibleItemIndex != target.index ||
                translationListState.firstVisibleItemScrollOffset != target.offsetPx
            ) {
                scrollFollower(
                    coordinator,
                    ScrollSyncCoordinator.Pane.BOTTOM,
                    translationListState,
                    target,
                )
            }
        }
    }

    // Case 1: first composition (covers rotation and scroll restore) and any later movement of
    // the top pane's rest position.
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(100)
            .collect { alignBottomToTop() }
    }

    // Case 2: a batch of translations arriving while the reader sits still. This changes ONLY the
    // bottom pane's item heights — the top pane's position never moves — so the position-keyed
    // effect above never fires for it; that is what its own comment used to (wrongly) claim was
    // covered. `chapterTranslations` as the LaunchedEffect key is what actually reacts to this.
    LaunchedEffect(chapterTranslations) {
        withFrameNanos {} // let the new translations finish composing/measuring first
        alignBottomToTop()
    }

    Box(
        modifier = modifier
            .testTag(VERTICAL_SPREAD_ROOT)
            .onSizeChanged { paneAreaHeightPx = it.height },
    ) {
        Column(Modifier.fillMaxSize().background(palette.bg).testTag(VERTICAL_BACKGROUND)) {

            // Top pane — original, fully interactive. Applied at the pane, not VerticalBookSpread's
            // root — the root carries the spread's background; insetting it would leave a bare band
            // beside it (Phase 2b defect #4). `windowInsetsPadding` sits between `weight` and
            // `testTag`, deliberately NOT "testTag first": `weight` gives this LazyColumn an EXACT
            // height, and a LayoutModifier measured with exact incoming constraints always reports
            // that exact size back to its parent regardless of what it does internally — so a
            // testTag placed outside (before) windowInsetsPadding here reports the pane's full
            // weighted slot unconditionally and cannot see the inset at all — measured directly
            // while writing VerticalReaderInsetTest: with the inset applied and testTag first,
            // `VERTICAL_TOP_PANE`'s reported top stayed 0, identical to no inset at all. testTag has
            // to sit on the shrunk child windowInsetsPadding actually measures, i.e. last.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(effectiveRatio)
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .testTag(VERTICAL_TOP_PANE),
            ) {
                bookItems(
                    book = book,
                    showIllustrations = showIllustrations,
                    masthead = { i, ch -> ChapterMasthead(chapter = ch, chapterIndex = i) },
                    image = { _, _, img -> Illustration(path = img.path) },
                    paragraph = { chapterIndex, idx, original ->
                        val isSelected = wordSelection?.chapterIndex == chapterIndex &&
                            wordSelection.paragraphIndex == idx
                        val (selectedStart, selectedEnd) = wordSelection
                            ?.takeIf { isSelected }
                            ?.let { it.startChar to it.endChar }
                            ?: (-1 to -1)
                        Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                            ParagraphItem(
                                text = original,
                                index = idx,
                                isFirstOfChapter = idx == 0,
                                isOriginal = true,
                                isActive = isSelected,
                                selectedWordStart = selectedStart,
                                selectedWordEnd = selectedEnd,
                                style = style,
                                wordHighlightEnabled = wordHighlightEnabled,
                                onWordSelected = { word, start, end -> onWordSelected(word, chapterIndex, idx, start, end) },
                                onSelectionDragged = { start, end -> onSelectionDragged(start, end) },
                                onTap = { if (wordSelection != null) onDismiss() else onToggleBars() },
                            )
                        }
                        Spacer(Modifier.height(style.paragraphSpacing.dp))
                    },
                )
            }

            HorizontalDividerHandle(
                ratio = effectiveRatio,
                paneAreaHeightPx = paneAreaHeightPx,
                prominent = barsVisible || !dragRatio.isNaN(),
                onDrag = { dragRatio = it },
                onDragFinished = {
                    if (!dragRatio.isNaN()) { onSetVerticalSplitRatio(dragRatio); dragRatio = Float.NaN }
                },
                onTap = onToggleBars,
            )

            // Bottom pane — translation, read-only. The reader draws edge-to-edge and does not
            // consume its own bottom inset anywhere else (TranslationBubble's navigationBarsPadding
            // covers only the word-selection popup, not this pane), so without this the last line
            // of the translation sits under the gesture bar. Same placement rule as the top pane:
            // last in the chain, after `weight`, and on the pane itself rather than the spread's
            // root.
            LazyColumn(
                state = translationListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - effectiveRatio)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .testTag(VERTICAL_BOTTOM_PANE),
            ) {
                bookItems(
                    book = book,
                    showIllustrations = showIllustrations,
                    masthead = { i, ch -> ChapterMasthead(chapter = ch, chapterIndex = i) },
                    // Parity item, not a re-decode: the index must exist in this pane or every
                    // later index shifts, but the picture itself is already on screen above.
                    image = { _, _, _ -> Spacer(Modifier.fillMaxWidth().height(24.dp)) },
                    paragraph = { chapterIndex, idx, original ->
                        val translated = chapterTranslations[chapterIndex]?.getOrElse(idx) { "" } ?: ""
                        val awaiting = original.isNotBlank() && translated.isEmpty()
                        val isSelected = wordSelection?.chapterIndex == chapterIndex &&
                            wordSelection.paragraphIndex == idx
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .alpha(if (wordSelection != null && !isSelected) 0.2f else 1f)
                        ) {
                            // Two nested alphas, matching the landscape spread exactly: the outer
                            // one dims OTHER paragraphs' translations while a bubble is open, this
                            // inner one dims THE SELECTED paragraph's, because the bubble already
                            // shows that translation and would otherwise compete with it. Without
                            // it the selected row is the brightest thing on the pane — the inverse
                            // of the intent.
                            Box(Modifier.alpha(if (isSelected) 0.25f else 1f)) {
                            Crossfade(
                                targetState = awaiting,
                                animationSpec = tween(MotionTokens.Medium, easing = MotionTokens.EaseStandard),
                                label = "translationResolve",
                            ) { isAwaiting ->
                                if (isAwaiting) {
                                    TranslationPlaceholder(style = style)
                                } else {
                                    ParagraphItem(
                                        text = translated,
                                        index = idx,
                                        isFirstOfChapter = idx == 0,
                                        isOriginal = false,
                                        isActive = false,
                                        selectedWordStart = -1,
                                        selectedWordEnd = -1,
                                        style = style,
                                        onWordSelected = { _, _, _ -> },
                                        onTap = { if (wordSelection != null) onDismiss() else onToggleBars() },
                                    )
                                }
                            }
                            }
                        }
                        Spacer(Modifier.height(style.paragraphSpacing.dp))
                    },
                )
            }
        }

        if (wordSelection != null) {
            TranslationBubble(
                wordSelection = wordSelection,
                onSave = { onSaveWord(wordSelection.word, wordSelection.chapterIndex, wordSelection.paragraphIndex) },
                onSpeak = { onSpeak(wordSelection.word, sourceLang.code) },
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

/**
 * Drives [followerState] to [target] with the coordinator masked, swallowing **only** the
 * cancellation that means "somebody else took this list's scroll".
 *
 * Why the catch exists, and why it must not be a bare `catch (e: Exception)`:
 * `LazyListState.scrollToItem` goes through `MutatorMutex`. A competing scroll at
 * `MutatePriority.UserInput` — an ordinary finger landing on the follower pane while this
 * programmatic scroll is in flight — cancels this one with `MutationInterruptedException`, which
 * **is a `CancellationException`**, and `mutate` is a `coroutineScope`, so it is rethrown into the
 * caller. Callers here are `collect` lambdas inside `LaunchedEffect`s whose keys never change
 * while the reader is composed: an escaping cancellation therefore completes the effect for good,
 * and scroll sync dies silently for the rest of the session — no crash, no log, nothing a user
 * could report beyond "it stopped following". Verified against Compose Foundation 1.7.3 sources.
 *
 * `ensureActive()` is what keeps this honest: if *this* coroutine was the one cancelled (the
 * composable is leaving), the exception is rethrown and the effect ends as it should. Only a
 * cancellation that came from the mutex is absorbed.
 */
private suspend fun scrollFollower(
    coordinator: ScrollSyncCoordinator,
    pane: ScrollSyncCoordinator.Pane,
    followerState: LazyListState,
    target: FollowerTarget,
) {
    try {
        coordinator.beginProgrammaticScroll(pane)
        try {
            followerState.scrollToItem(target.index, target.offsetPx)
        } finally {
            coordinator.endProgrammaticScroll(pane)
        }
    } catch (cancellation: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/**
 * The stacked layout's divider: a real 28dp row between the two panes, not an overlay. Unlike the
 * landscape [DividerHandle], nothing scrolls underneath it, so it does not need to forward
 * vertical drags into a list — the vertical drag here *is* the ratio gesture.
 */
/** The divider row's own height. The drag math subtracts it, so the two must not drift apart. */
private val HANDLE_HEIGHT = 28.dp

@Composable
private fun HorizontalDividerHandle(
    ratio: Float,
    paneAreaHeightPx: Int,
    prominent: Boolean,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onTap: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val gripAlpha by animateFloatAsState(
        targetValue = if (prominent) 0.9f else 0.25f,
        label = "verticalDividerHandleAlpha",
    )
    val handleHeightPx = with(LocalDensity.current) { HANDLE_HEIGHT.toPx() }
    val currentPaneAreaHeightPx by rememberUpdatedState(paneAreaHeightPx.toFloat())
    val currentRatio by rememberUpdatedState(ratio)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = Modifier
            .testTag(VERTICAL_DIVIDER)
            .fillMaxWidth()
            .height(HANDLE_HEIGHT)
            .pointerInput(Unit) { detectTapGestures { currentOnTap() } }
            // Keyed on Unit, NOT on the height: re-keying restarts the pointerInput coroutine, and
            // a coroutine cancelled mid-drag fires neither onDragEnd nor onDragCancel — leaving
            // dragRatio non-NaN forever, so the divider stays pinned to an uncommitted value and
            // the grip stays lit after the bars hide. A window resize, the IME or a fold does
            // exactly that. The height is read through rememberUpdatedState instead, like the
            // other four values here. The landscape handle guards the same case with try/finally.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { currentOnDragFinished() },
                    onDragCancel = { currentOnDragFinished() },
                ) { _, dy ->
                    // The two panes share the area MINUS this handle's own row, so the ratio must
                    // be taken over that, not over the whole spread — otherwise the divider trails
                    // the finger by handleHeight/totalHeight (~3.5% on a phone).
                    val paneExtentPx = currentPaneAreaHeightPx - handleHeightPx
                    if (paneExtentPx > 0f) {
                        currentOnDrag(
                            DividerDragMath.newVerticalRatio(currentRatio, dy, paneExtentPx)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .alpha(gripAlpha)
                .background(palette.rule, RoundedCornerShape(2.dp))
        )
    }
}
