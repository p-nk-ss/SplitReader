package com.example.splitreader.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.Newsreader

/**
 * Bookplate — primary button for reading CTAs.
 *
 * Solid warm-ink stamp with a hairline cream rule inside and a 1 px ink
 * outline outside (a 2 px gap between). Italic Newsreader label. A small
 * terracotta diamond ornament punctuates the label, echoing the chapter
 * divider used inside the reader.
 *
 * Use for reading-flow actions only:
 *
 *   BookplateButton(text = "Continue reading",       onClick = ...)
 *   BookplateButton(text = "Resume from Chapter 12", onClick = ...)
 *   BookplateButton(text = "Open chapter",           onClick = ...)
 *
 * For system actions (Open book, Browse files, Save settings) use
 * [LibraryTagButton] instead — the two vocabularies stay distinct.
 */
@Composable
fun BookplateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val outerRadius = 8.dp
    val innerRadius = 4.dp
    val horizontalPad = if (dense) 16.dp else 22.dp
    val verticalPad   = if (dense) 9.dp else 12.dp
    val labelSize = if (dense) 13.sp else 15.sp
    val palette = LocalReaderPalette.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(outerRadius),
        color = palette.bg,
        border = BorderStroke(1.dp, palette.ink),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .clip(RoundedCornerShape(innerRadius))
                .background(palette.ink)
                .border(1.dp, palette.bg.copy(alpha = 0.18f), RoundedCornerShape(innerRadius))
                .padding(PaddingValues(horizontal = horizontalPad, vertical = verticalPad)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    color = palette.bg,
                    fontFamily = Newsreader,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    fontSize = labelSize,
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .rotate(45f)
                        .background(palette.accent),
                )
            }
        }
    }
}
