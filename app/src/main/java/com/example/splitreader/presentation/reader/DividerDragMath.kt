package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.ReadingDefaults

/**
 * Pure math for the draggable split divider: converts a horizontal drag delta (px) into a new
 * split ratio, clamped to [ReadingDefaults.SPLIT_RATIO_RANGE]. Free of Compose types so it is
 * JVM-testable; the same range is enforced again by ReaderViewModel.setSplitRatio on persist,
 * so UI and persistence cannot drift.
 */
object DividerDragMath {
    fun newRatio(current: Float, deltaPx: Float, paneWidthPx: Float): Float =
        clamped(current, deltaPx, paneWidthPx, ReadingDefaults.SPLIT_RATIO_RANGE)

    /**
     * The stacked reader's divider: a vertical drag delta over the pane *height*, clamped to
     * [ReadingDefaults.VERTICAL_SPLIT_RATIO_RANGE].
     *
     * Same arithmetic as [newRatio] but a separate entry point on purpose — the two ratios are
     * separate preferences, so a future change to one range must not silently move the other.
     * Note the two ranges hold identical numbers today, so this separation is enforced by the
     * code reading two named constants, not by any test; see `DividerDragMathTest`.
     */
    fun newVerticalRatio(current: Float, deltaPx: Float, paneHeightPx: Float): Float =
        clamped(current, deltaPx, paneHeightPx, ReadingDefaults.VERTICAL_SPLIT_RATIO_RANGE)

    private fun clamped(
        current: Float,
        deltaPx: Float,
        extentPx: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float {
        if (extentPx <= 0f) return current
        return (current + deltaPx / extentPx).coerceIn(range)
    }
}
