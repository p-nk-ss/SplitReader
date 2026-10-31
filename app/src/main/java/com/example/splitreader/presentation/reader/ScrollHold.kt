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

/** How long a jump keeps re-anchoring on its target while the rows around it settle. */
internal const val JUMP_HOLD_MILLIS = 4_000L

/**
 * Scrolls to [index]/[offset] and keeps it there while the rows around it are still settling.
 *
 * Right after a jump, rows below the target can still be short — in the side-by-side layout a row
 * is as tall as its taller half, and the translation half is a placeholder until it arrives;
 * images decode later too. Near the end of a book the list then cannot put the target at the top
 * and clamps to an earlier item, and when the rows grow it stays there (and that earlier
 * paragraph is what gets saved). So for [JUMP_HOLD_MILLIS], every layout change re-applies the
 * target if the list has drifted from it. The user's first drag ends the hold at once: from then
 * on the position is theirs.
 */
internal suspend fun LazyListState.scrollToItemAndHold(index: Int, offset: Int) {
    scrollToItem(index, offset)
    withTimeoutOrNull(JUMP_HOLD_MILLIS) {
        coroutineScope {
            launch {
                interactionSource.interactions.first { it is DragInteraction.Start }
                this@coroutineScope.cancel()
            }
            snapshotFlow { layoutInfo.visibleItemsInfo.map { it.index to it.size } }
                .distinctUntilChanged()
                .collect {
                    if (firstVisibleItemIndex != index || firstVisibleItemScrollOffset != offset) {
                        scrollToItem(index, offset)
                    }
                }
        }
    }
}
