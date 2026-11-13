package com.example.splitreader.presentation.reader

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Upper bound on how long a jump keeps re-anchoring on its target while the rows around it settle.
 * Generous on purpose (slow providers, a cold Offline HQ model): the hold already ends the moment
 * anything else moves the list, so a long window cannot fight the user.
 */
internal const val JUMP_HOLD_MILLIS = 10_000L

/**
 * Scrolls to [index]/[offset] and keeps it there while the rows around it are still settling.
 *
 * Right after a jump, rows below the target can still be short — in the side-by-side layout a row
 * is as tall as its taller half, and the translation half is a placeholder until it arrives;
 * images decode later too. Near the end of a book the list then cannot put the target at the top
 * and clamps to an earlier item, and when the rows grow it stays there (and that earlier paragraph
 * is what gets saved). So, on each layout change, the target is re-applied if the list has drifted
 * from it.
 *
 * Only while the list sits where this function last left it, or is still pinned at its end: rows
 * growing below the first visible item do not move that item, and a list that merely re-clamps (a
 * taller viewport when the reader's bars auto-hide, a row shrinking) stays at the end — both keep
 * the hold going. ANY other scroll — a drag, a fling, the stacked
 * layout's pane sync, a divider-strip drag (raw deltas, no DragInteraction), a mouse wheel,
 * TalkBack — leaves the list somewhere it can still scroll forward from and ends the hold for good.
 * The drag listener just ends it a frame earlier.
 */
internal suspend fun LazyListState.scrollToItemAndHold(index: Int, offset: Int) {
    scrollToItem(index, offset)
    var leftAt = firstVisibleItemIndex to firstVisibleItemScrollOffset
    withTimeoutOrNull(JUMP_HOLD_MILLIS) {
        coroutineScope {
            launch {
                interactionSource.interactions.first { it is DragInteraction.Start }
                this@coroutineScope.cancel()
            }
            snapshotFlow { layoutInfo.visibleItemsInfo.map { it.index to it.size } }
                .distinctUntilChanged()
                .collect {
                    val now = firstVisibleItemIndex to firstVisibleItemScrollOffset
                    if (now == index to offset) return@collect
                    // Moved, and not just re-clamped at the end (a taller viewport when the bars
                    // auto-hide, a row shrinking): someone else scrolled, the position is theirs.
                    if (now != leftAt && canScrollForward) {
                        this@coroutineScope.cancel()
                        return@collect
                    }
                    scrollToItem(index, offset)
                    leftAt = firstVisibleItemIndex to firstVisibleItemScrollOffset
                }
        }
    }
}
