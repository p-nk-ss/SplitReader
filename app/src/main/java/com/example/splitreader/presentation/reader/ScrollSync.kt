package com.example.splitreader.presentation.reader

/**
 * Decides which of the two stacked reader panes is currently driving the other.
 *
 * Free of Compose types so the arbitration — historically the part of a two-pane sync that
 * breaks — is unit-testable rather than only observable by scrolling on a device.
 */
class ScrollSyncCoordinator {

    enum class Pane { TOP, BOTTOM }

    private var leader: Pane? = null
    private var masked: Pane? = null

    fun leader(): Pane? = leader

    fun follower(): Pane? = when (leader) {
        Pane.TOP -> Pane.BOTTOM
        Pane.BOTTOM -> Pane.TOP
        null -> null
    }

    /**
     * Feed every change of a pane's `isScrollInProgress`. Returns true when [pane] holds
     * leadership afterwards.
     *
     * A masked pane can never *acquire* leadership (that motion came from us, not the user) but
     * can still *release* it, so a pane masked mid-gesture does not get stuck as leader forever.
     */
    fun onScrollStateChanged(pane: Pane, scrolling: Boolean): Boolean {
        if (scrolling) {
            if (pane == masked) return false
            if (leader == null) leader = pane
        } else {
            if (leader == pane) leader = null
        }
        return leader == pane
    }

    fun beginProgrammaticScroll(pane: Pane) { masked = pane }

    fun endProgrammaticScroll(pane: Pane) { if (masked == pane) masked = null }
}

/** Where the follower should sit: same item, offset rescaled to that item's own height. */
data class FollowerTarget(val index: Int, val offsetPx: Int)

/**
 * Alignment is **proportional inside the item, exact at its boundaries**. A translated paragraph
 * is rarely the same height as its original, so matching them line-for-line is not possible;
 * paragraph tops always agree and the middle of a long paragraph drifts. That is the accepted
 * cost of the stacked layout.
 *
 * [followerItemH] is null until that item has been composed in the follower; the leader's height
 * is then a first estimate and the next emission corrects it.
 */
fun computeFollowerTarget(
    leaderFirstIndex: Int,
    leaderOffsetPx: Int,
    leaderItemH: Int,
    followerItemH: Int?,
): FollowerTarget {
    val fraction =
        if (leaderItemH <= 0) 0f
        else (leaderOffsetPx.toFloat() / leaderItemH).coerceIn(0f, 0.95f)
    val height = followerItemH ?: leaderItemH
    return FollowerTarget(leaderFirstIndex, (fraction * height).toInt())
}
