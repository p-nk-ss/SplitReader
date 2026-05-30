package com.example.splitreader.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalReaderPalette

/**
 * Library tag — primary button for system actions.
 *
 * Cream tag with a 1 px deep-ink border, 4 dp corners, a small terracotta
 * diamond at the leading edge, and a JetBrains Mono uppercase label.
 * Optional + glyph for "Open book" / "Add to library" style actions.
 *
 * Use for system / file / catalogue actions:
 *
 *   LibraryTagButton(text = "Open book",   onClick = ...)
 *   LibraryTagButton(text = "Browse files", onClick = ..., showPlus = false)
 *   LibraryTagButton(text = "Save", onClick = ..., dense = true, showPlus = false)
 *
 * Reading CTAs ("Continue reading", "Open chapter") belong to
 * [BookplateButton] instead so the two vocabularies don't compete.
 */
@Composable
fun LibraryTagButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPlus: Boolean = true,
    dense: Boolean = false,
) {
    val verticalPad   = if (dense) 7.dp else 9.dp
    val labelSize     = if (dense) 10.sp else 11.sp
    val plusSize      = if (dense) 12.dp else 13.dp
    val palette       = LocalReaderPalette.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = palette.bg2,
        border = BorderStroke(1.dp, palette.ink),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                PaddingValues(start = 14.dp, top = verticalPad, end = 16.dp, bottom = verticalPad),
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .rotate(45f)
                    .background(palette.accent),
            )
            Text(
                text = text.uppercase(),
                color = palette.ink,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = labelSize,
                letterSpacing = 1.2.sp,
            )
            if (showPlus) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier.size(plusSize),
                )
            }
        }
    }
}
