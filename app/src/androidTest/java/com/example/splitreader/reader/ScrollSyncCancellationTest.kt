package com.example.splitreader.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `LazyListState.scrollToItem` goes through `MutatorMutex`. A competing scroll at
 * `MutatePriority.UserInput` — a finger landing on the follower pane while the programmatic scroll
 * is in flight — cancels it with `MutationInterruptedException`, which **is** a
 * `CancellationException`. If that escapes the collector it completes the `LaunchedEffect` for
 * good: its keys never change while the reader is composed, so sync dies silently for the rest of
 * the session, with no crash and nothing in the log.
 *
 * `scrollFollower` absorbs exactly that cancellation. This is the test the JVM suite could not
 * write — it was attempted there and deleted, because it passed identically with and without the
 * guard.
 *
 * **Provocation strengthened from the brief's two sequential `swipeUp()` calls.** Measured on a
 * real device via temporary logcat instrumentation (`FOLLOW-BEGIN` / `FOLLOW-DONE` /
 * `FOLLOW-CANCELLED` around `scrollFollower`, not committed): two sequential
 * `topPane.performTouchInput { swipeUp() }` / `bottomPane.performTouchInput { swipeUp() }` calls
 * do **not** overlap. `performTouchInput` on this harness does not return until the resulting
 * scroll — including the fling that follows a swipe — has settled, so by the time the second call
 * starts injecting, the first pane's programmatic follower-drive is long finished (observed gap:
 * over 1 second between the leader going idle and the second call's first event, across repeated
 * runs). Zero `FOLLOW-CANCELLED` events were ever logged with the sequential form — the guard was
 * never exercised, on either side of removing it, which is why removing it reproduced the exact
 * same (wrong) result as leaving it in.
 *
 * The fix is one real gesture with two simultaneous pointers, injected as a single
 * `performTouchInput` block against the root (a single call = one uninterrupted event stream, so
 * there is no idle-synchronization gap for the second pointer to miss): pointer 1 drags the top
 * pane first, which starts the leader-driven follower scroll on the bottom pane; pointer 2 then
 * comes down on the bottom pane and drags it *while pointer 1 is still moving*, landing squarely
 * inside the follower's in-flight `scrollToItem`.
 *
 * **Both drags end with a pause before lift-off (`advanceEventTime`), deliberately avoiding a
 * fling.** A first version of this gesture (many larger `moveBy` steps, released at full
 * velocity) reproduced the cancellation identically but then triggered exactly the multi-minute
 * decay tail Task 3 measured (`isScrollInProgress` staying `true` long after the visible position
 * stops moving) — one run's leader pane took over 100s of real wall time just to go idle after a
 * single gesture, on top of the tail itself, blowing well past `awaitSettled`'s ceiling and
 * failing the whole `ActivityScenario` teardown, not just the assertion. Pausing before `up()`
 * lets the velocity tracker decay to ~zero so lifting doesn't hand the list any residual momentum
 * to fling with — the recognizer sees the drag end, not a throw. This is orthogonal to the defect
 * under test: the cancellation still happens (a competing `MutatePriority.UserInput` mutation
 * always wins over the follower's `Default`-priority one, fling or no fling), it just does not
 * saddle the assertion with the host's decay-animation tail on top of it.
 *
 * **The distance floor matters as much as the overlap.** An early version of this gesture moved
 * only ~150px total — enough to reproduce `FOLLOW-CANCELLED`, but never enough to cross out of
 * item 0 (its masthead + first paragraph render taller than that on this fixture), so
 * `firstVisibleItemIndex` stayed `0` for both panes for the entire test regardless of what
 * `scrollFollower` did. Two full runs with that gesture — one with the guard removed (a plain
 * rerun) and one with it present — both reported `tests="1" failures="0"`, but neither is evidence
 * about the guard: the assertion was comparing `0` to `0` no matter what happened underneath. Once
 * the gesture was widened to ~700px (crossing several item boundaries, confirmed via the same
 * temporary logcat instrumentation), the same two configurations produced a real, decisive
 * contrast:
 *
 * - **Guard present** (`scrollFollower`'s `try/catch(CancellationException)` intact): 13 real
 *   `MutationInterruptedException`s ("Current mutation had a higher priority"), each absorbed
 *   cleanly; the leader-driven effect kept running and both panes converged on item 8. `tests="1"
 *   failures="0"`, 222.9s.
 * - **Guard removed** (only `beginProgrammaticScroll`/`scrollToItem`/`endProgrammaticScroll` left,
 *   as this task's brief prescribes): logcat shows `FOLLOW-BEGIN pane=BOTTOM target=0/76` while
 *   the competing finger's `isScrollInProgress=true` was already active, with **no** matching
 *   `FOLLOW-DONE` and **no** `LEAD-EFFECT` line ever logged again for the rest of the test — the
 *   leader-driven `LaunchedEffect` died exactly as the class doc predicts. The top pane kept moving
 *   under direct touch input to item 8; the bottom pane, no longer driven by anything, stayed
 *   frozen at item 1 from that point on. `tests="1" failures="1"`:
 *   `AssertionError: ... left the top pane at 8 and the bottom pane at 1 ... expected:<8> but
 *   was:<1>`. Reverting the guard and rerunning the identical gesture on a pristine tree returned
 *   to green (`tests="1" failures="0"`, 6.98s on a freshly booted emulator).
 *
 * That is one clean guard-present/guard-removed pair at the distance that actually exercises the
 * assertion — not a large sample. Whether this exact overlap reproduces on every run wasn't
 * re-measured at this distance (each run costs several minutes, and the host alternated between
 * merely slow and briefly unusable — see the ActivityScenario/ANR notes in the task report). Two
 * earlier runs at the too-small (~150px) distance also came back green with the guard removed, but
 * per above they carry no signal either way — the assertion couldn't have failed there regardless
 * of the guard, so they are not a second and third "guard removed, still green" data point. Treat
 * this test as a real net for "the leader-driven sync effect died," proven capable of catching
 * that failure mode once, not as a statistically characterized flake rate.
 *
 * **Movement floor, enforced at runtime.** The failure mode above — a provocation that never
 * leaves item 0, making the index equality check vacuously true — is not just a development-time
 * mistake; a different device or font/density combination rendering this fixture's items shorter
 * or taller than the ones measured here could reproduce it silently. `assertTrue(... topIndex >
 * 0 ...)` after the provocation and `assertTrue(... settled.topIndex > afterProvocation.topIndex
 * ...)` after the recovery drag turn that silent, signal-free pass into a loud, specifically-named
 * failure instead, matching the idiom `ScrollSyncGestureTest` already uses for the same reason.
 */
@RunWith(AndroidJUnit4::class)
class ScrollSyncCancellationTest : ReaderGestureTest() {

    @Test
    fun syncSurvivesFingerLandingOnFollowerMidScroll() {
        assertPortraitStacked()
        val top = LazyListState()
        val bottom = LazyListState()
        composeSpread(top, bottom)

        val topStart = topPane.fetchSemanticsNode().boundsInRoot.let {
            Offset(it.center.x, it.bottom - 40f)
        }
        val bottomStart = bottomPane.fetchSemanticsNode().boundsInRoot.center

        // Provoke: one real gesture, two simultaneous pointers, one uninterrupted event stream —
        // pointer 1 (top) leads, pointer 2 (bottom) lands and drags while pointer 1 is still
        // moving, catching the follower's programmatic scrollToItem in flight. Paused before
        // release so neither pointer hands the list a fling on lift-off. Enough total distance
        // (14 steps * 50px = 700px for pointer 1) to cross at least one item boundary — a first
        // version moved only ~150px, which never left item 0 (its masthead + first paragraph are
        // taller than that), so `firstVisibleItemIndex` never changed and the index-only assertion
        // below could not have failed no matter what `scrollFollower` did; it was measuring nothing.
        composeRule.onRoot().performTouchInput {
            down(1, topStart)
            moveBy(1, Offset(0f, -50f))
            moveBy(1, Offset(0f, -50f))
            down(2, bottomStart)
            repeat(6) {
                moveBy(2, Offset(0f, -50f))
                moveBy(1, Offset(0f, -50f))
            }
            repeat(6) { moveBy(1, Offset(0f, -50f)) }
            advanceEventTime(200)
            up(2)
            up(1)
        }
        val afterProvocation = awaitSettled(top, bottom)

        // Movement floor: the whole point of this test is comparing indices, and that comparison
        // is vacuous if the provocation never left item 0 — exactly the trap that produced two
        // worthless "green" runs during development of this test (see the class doc). A different
        // device/density rendering the fixture's items shorter or taller than this one could
        // under- or over-travel the same pixel distances; failing loudly here means a future
        // under-travel shows up as a clear, named failure instead of a silent, signal-free pass.
        assertTrue(
            "The provocation gesture did not move the top pane past item 0 (still at " +
                "${afterProvocation.topIndex}) — the distance floor from this test's KDoc was not " +
                "met on this device/density, so the assertion below would compare index 0 to " +
                "index 0 regardless of the guard. Widen the gesture's travel distance.",
            afterProvocation.topIndex > 0,
        )

        // The assertion is about what happens AFTERWARDS: a later gesture must still propagate.
        // Same paused-release shape, and the same distance floor, for the same reason.
        composeRule.onRoot().performTouchInput {
            down(1, topStart)
            repeat(10) { moveBy(1, Offset(0f, -50f)) }
            advanceEventTime(200)
            up(1)
        }
        val settled = awaitSettled(top, bottom)

        assertTrue(
            "The recovery drag did not move the top pane at all (still at ${settled.topIndex}, " +
                "was ${afterProvocation.topIndex}) — the gesture missed the pane or the distance " +
                "floor was not met, so the equality check below carries no signal.",
            settled.topIndex > afterProvocation.topIndex,
        )
        assertEquals(
            "After a competing gesture, a later drag left the top pane at ${settled.topIndex} and " +
                "the bottom pane at ${settled.bottomIndex}. Sync stopped working — the " +
                "cancellation escaped and completed the LaunchedEffect permanently.",
            settled.topIndex, settled.bottomIndex,
        )
    }
}
