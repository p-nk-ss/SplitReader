package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Found on a tablet: jumping to a bookmark near the end of a book landed ~5 paragraphs early. In
 * the side-by-side layout a row is as tall as its taller half, and rows below the target were
 * still short (translations not in yet, images not decoded), so the list could not put the target
 * at the top and clamped to an earlier item. When the rows then grew, the list stayed there — and
 * that earlier paragraph was saved as the reading position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScrollToItemAndHoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val count = 40
    private val target = 35

    /** Item heights in dp; the tail (≥ 30) starts short, as untranslated rows do. */
    private val heights = mutableStateListOf<Int>().apply {
        repeat(count) { i -> add(if (i >= 30) 10 else 60) }
    }

    private fun compose(hold: Boolean): () -> LazyListState {
        lateinit var state: LazyListState
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = rememberLazyListState()
            Box(Modifier.size(320.dp, 400.dp)) {
                LazyColumn(state = state, modifier = Modifier.testTag("list")) {
                    items(count) { i -> Box(Modifier.fillMaxWidth().height(heights[i].dp)) }
                }
            }
            LaunchedEffect(Unit) {
                if (hold) state.scrollToItemAndHold(target, 0) else state.scrollToItem(target, 0)
            }
        }
        composeRule.mainClock.advanceTimeBy(100)
        return { state }
    }

    private fun growTail() {
        composeRule.runOnUiThread {
            for (i in 30 until count) heights[i] = 120
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(200)
    }

    @Test
    fun `without holding, a jump near the end stays on an earlier item once rows grow`() {
        val state = compose(hold = false)
        growTail()
        // Documents the defect the hold exists for (the plain scroll is the old behaviour).
        assertNotEquals(target, state().firstVisibleItemIndex)
    }

    @Test
    fun `holding re-anchors the target when rows below it grow`() {
        val state = compose(hold = true)
        assertNotEquals("precondition: the short tail clamps the first jump", target, state().firstVisibleItemIndex)
        growTail()
        assertEquals(target, state().firstVisibleItemIndex)
        assertEquals(0, state().firstVisibleItemScrollOffset)
    }

    /**
     * The user's own scroll wins: once they drag, a settling layout must not yank them back.
     *
     * Pins the behaviour, not the drag listener in [scrollToItemAndHold]: a real drag also
     * interrupts the hold's own `scrollToItem` through the list's mutator, which ends the hold by
     * itself. Removing the listener alone leaves this green (observed); the listener stays as the
     * explicit guarantee for a drag that starts while the hold is not scrolling.
     */
    @Test
    fun `after a user drag a settling layout does not pull the list back`() {
        val state = compose(hold = true)
        composeRule.onNodeWithTag("list").performTouchInput { swipeDown() }
        composeRule.mainClock.advanceTimeBy(2_000) // let the fling settle; still inside the hold window
        val afterDrag = state().firstVisibleItemIndex
        // Grow every row, the visible ones too, so a still-running hold would see it and pull back.
        composeRule.runOnUiThread {
            for (i in 0 until count) heights[i] = 120
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(200)
        assertNotEquals(target, state().firstVisibleItemIndex)
        assertEquals(afterDrag, state().firstVisibleItemIndex)
    }

    @Test
    fun `the hold lets go after its window`() {
        val state = compose(hold = true)
        composeRule.mainClock.advanceTimeBy(JUMP_HOLD_MILLIS + 500)
        growTail()
        assertNotEquals(target, state().firstVisibleItemIndex)
    }
}
