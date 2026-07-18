package com.example.splitreader.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
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

// ── Paragraph actions overlay ─────────────────────────────────────────────

@Composable
private fun ParagraphActionsOverlay(
    wordSelection: WordSelection,
    onDismiss: () -> Unit,
    onSaveWord: (String) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    var wordInput by remember(wordSelection.word) { mutableStateOf(wordSelection.word) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 64.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(radii.lg))
                .background(palette.bg2)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.lg))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                .padding(sp.md),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED · ${palette.key.name}",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = palette.accent,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Editable word/phrase field
                    BasicTextField(
                        value = wordInput,
                        onValueChange = { wordInput = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = Newsreader,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            fontSize = 26.sp,
                            color = palette.ink,
                        ),
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .drawBehind {
                                drawLine(
                                    color = palette.edge,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                            .padding(bottom = 3.dp),
                    )
                }
                // 48dp touch target around the 30dp visual close circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, palette.edge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Close", tint = palette.ink2, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(sp.sm))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
            Spacer(Modifier.height(sp.sm))

            // Translation section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "TRANSLATION",
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
                if (wordSelection.translation == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = palette.ink3,
                    )
                } else {
                    Text(
                        text = wordSelection.translation,
                        fontFamily = Newsreader,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        color = palette.ink,
                    )
                }
            }

            Spacer(Modifier.height(sp.sm))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
            Spacer(Modifier.height(sp.sm))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val actions = listOf(
                    Triple(Icons.Outlined.Style, "Save word", { onSaveWord(wordInput); onDismiss() }),
                    Triple(Icons.Outlined.BorderColor, "Highlight", null as (() -> Unit)?),
                    Triple(Icons.Outlined.BookmarkBorder, "Bookmark", null as (() -> Unit)?),
                    Triple(Icons.Outlined.EditNote, "Add note", null as (() -> Unit)?),
                    Triple(Icons.Outlined.Headphones, "Listen", null as (() -> Unit)?),
                )
                actions.forEach { (icon, label, action) ->
                    val enabled = action != null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (enabled) palette.edge else palette.edge.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .then(if (enabled) Modifier.clickable { action?.invoke() } else Modifier)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(icon, null, tint = if (enabled) palette.ink2 else palette.ink4, modifier = Modifier.size(18.dp))
                            Text(
                                text = label,
                                fontFamily = Newsreader,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp,
                                color = if (enabled) palette.ink2 else palette.ink4,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}