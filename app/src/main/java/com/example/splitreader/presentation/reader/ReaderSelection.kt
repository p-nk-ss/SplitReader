package com.example.splitreader.presentation.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import java.text.BreakIterator

internal enum class HandleSide { START, END }

/**
 * Returns the visual (x, y) anchor for a selection handle at the given offset.
 *
 * Uses [TextLayoutResult.getPathForRange] (the same API the highlight uses, backed by
 * `Layout.getSelectionPath`) instead of [TextLayoutResult.getBoundingBox], because the
 * latter is backed by `Layout.getPrimaryHorizontal` which in Compose returns un-justified
 * char positions even when `textAlign = TextAlign.Justify`. The selection path always
 * matches the visually rendered glyph positions.
 *
 * - For [isStart] = true: returns (left edge of the char at [offset], bottom of its line).
 * - For [isStart] = false: returns (right edge of the char at [offset] - 1, bottom of its line).
 */
internal fun handleAnchor(
    layout: TextLayoutResult,
    text: String,
    offset: Int,
    isStart: Boolean,
): Pair<Float, Float> {
    if (text.isEmpty()) return 0f to 0f
    return if (isStart) {
        val rangeStart = offset.coerceIn(0, text.length - 1)
        val rangeEnd = (rangeStart + 1).coerceAtMost(text.length)
        val bounds = layout.getPathForRange(rangeStart, rangeEnd).getBounds()
        bounds.left to bounds.bottom
    } else {
        val rangeEnd = offset.coerceIn(1, text.length)
        val rangeStart = (rangeEnd - 1).coerceAtLeast(0)
        val bounds = layout.getPathForRange(rangeStart, rangeEnd).getBounds()
        bounds.right to bounds.bottom
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(
    color: Color,
    anchorX: Float,
    anchorY: Float,
    lineHeightPx: Float,
    circleRadiusPx: Float,
    lineStrokePx: Float,
) {
    drawLine(
        color = color,
        start = Offset(anchorX, anchorY),
        end = Offset(anchorX, anchorY + lineHeightPx),
        strokeWidth = lineStrokePx,
    )
    drawCircle(
        color = color,
        radius = circleRadiusPx,
        center = Offset(anchorX, anchorY + lineHeightPx + circleRadiusPx),
    )
}

internal fun snapToWordStart(charIndex: Int, text: String, iter: BreakIterator): Int {
    if (text.isEmpty()) return 0
    val safe = charIndex.coerceIn(0, text.length)
    if (safe == 0) return 0
    if (iter.isBoundary(safe)) return safe
    val prec = iter.preceding(safe)
    return if (prec == BreakIterator.DONE) 0 else prec
}

internal fun snapToWordEnd(charIndex: Int, text: String, iter: BreakIterator): Int {
    if (text.isEmpty()) return 0
    val safe = charIndex.coerceIn(0, text.length)
    if (safe == text.length) return text.length
    if (iter.isBoundary(safe)) return safe
    val fol = iter.following(safe)
    return if (fol == BreakIterator.DONE) text.length else fol
}
