package com.example.splitreader.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.PaperAccent
import com.example.splitreader.presentation.theme.PaperBg
import com.example.splitreader.presentation.theme.PaperInk2
import com.example.splitreader.presentation.theme.PaperInk3

/**
 * Ornament — the SplitReader brand mark.
 *
 * Identity glyph: ink-stamped tile with two warm-gold rules flanking a small
 * terracotta diamond, and "SPLIT" / "READER" set above and below in
 * JetBrains Mono. Three variants:
 *
 * - default (ink + terracotta + gold) — for normal UI
 * - inverted (cream + warm ink) — for foreground layers on light masks
 * - monochrome (warm-gold diamond) — for themed contexts (Android 13+)
 *
 * Size is driven by the modifier (use `.size(N.dp)`). The text labels and
 * stroke thicknesses scale proportionally with the icon's measured width.
 */
@Composable
fun BrandIcon(
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    monochrome: Boolean = false,
) {
    val bg          = if (inverted) PaperBg else BrandInk
    val rule        = if (inverted) PaperInk3 else BrandGold
    val diamond     = if (monochrome) rule else PaperAccent
    val labelColor  = if (inverted) PaperInk2 else BrandLabelGold
    val labelAlpha  = if (inverted) 0.85f else 0.62f

    BoxWithConstraints(modifier = modifier) {
        val size = maxWidth
        val cornerRadius = size * 0.236f
        val labelFontSize = (size.value * 0.10f).sp
        val labelTracking = (size.value * 0.012f).sp
        val labelInset = size * 0.19f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(bg),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val cy = h / 2f
                val ruleLen = w * 0.20f
                val ruleStroke = (size.value * 0.008f).coerceAtLeast(1f) * density

                drawLine(
                    color = rule.copy(alpha = 0.7f),
                    start = Offset(w * 0.15f, cy),
                    end = Offset(w * 0.15f + ruleLen, cy),
                    strokeWidth = ruleStroke,
                )
                drawLine(
                    color = rule.copy(alpha = 0.7f),
                    start = Offset(w - w * 0.15f - ruleLen, cy),
                    end = Offset(w - w * 0.15f, cy),
                    strokeWidth = ruleStroke,
                )

                val diaSize = w * 0.13f
                translate(left = w / 2f - diaSize / 2f, top = h / 2f - diaSize / 2f) {
                    rotate(degrees = 45f, pivot = Offset(diaSize / 2f, diaSize / 2f)) {
                        drawRect(color = diamond, size = Size(diaSize, diaSize))
                    }
                }
            }

            Text(
                text = "SPLIT",
                color = labelColor.copy(alpha = labelAlpha),
                fontFamily = JetBrainsMono,
                fontSize = labelFontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = labelTracking,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = labelInset),
            )
            Text(
                text = "READER",
                color = labelColor.copy(alpha = labelAlpha),
                fontFamily = JetBrainsMono,
                fontSize = labelFontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = labelTracking,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = labelInset),
            )
        }
    }
}

private val BrandInk        = Color(0xFF1E1A12)
private val BrandGold       = Color(0xFFC49645)
private val BrandLabelGold  = Color(0xFFE8DCB8)
