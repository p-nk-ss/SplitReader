package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.ReadingDefaults

/**
 * Pure math for the draggable split divider: converts a horizontal drag delta (px) into a new
 * split ratio, clamped to [ReadingDefaults.SPLIT_RATIO_RANGE]. Free of Compose types so it is
 * JVM-testable; the same range is enforced again by ReaderViewModel.setSplitRatio on persist,
 * so UI and persistence cannot drift.
 */
object DividerDragMath {
    fun newRatio(current: Float, deltaPx: Float, paneWidthPx: Float): Float {
        if (paneWidthPx <= 0f) return current
        return (current + deltaPx / paneWidthPx).coerceIn(ReadingDefaults.SPLIT_RATIO_RANGE)
    }
}
