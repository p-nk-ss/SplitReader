package com.example.splitreader.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.ReadingFont

/**
 * Shared settings widgets used by both the reader's in-session Display dialog and the global
 * Settings screen, so the two surfaces stay visually identical.
 */

@Composable
fun SectionEyebrow(text: String) {
    val palette = LocalReaderPalette.current
    Text(
        text = text.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = palette.ink3,
    )
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val palette = LocalReaderPalette.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = Newsreader, fontSize = 14.sp, color = palette.ink)
            Text(valueLabel, fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.3.sp, color = palette.ink3)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(22.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.bg,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.bg3,
            ),
        )
    }
}

/**
 * The full set of reading-typography controls (typeface, size, spacing, indent, justification,
 * hyphenation), shared verbatim by the reader's in-session Display dialog and the global Settings
 * screen. Values are absolute; the caller decides how to persist them.
 */
@Composable
fun TypographyControls(
    readingFont: ReadingFont,
    onSetReadingFont: (ReadingFont) -> Unit,
    textSize: Float,
    onSetTextSize: (Float) -> Unit,
    lineHeightMultiplier: Float,
    onSetLineHeight: (Float) -> Unit,
    letterSpacing: Float,
    onSetLetterSpacing: (Float) -> Unit,
    textIndent: Float,
    onSetTextIndent: (Float) -> Unit,
    paragraphSpacing: Float,
    onSetParagraphSpacing: (Float) -> Unit,
    justify: Boolean,
    onSetJustify: (Boolean) -> Unit,
) {
    val sp = LocalSpacing.current

    SectionEyebrow("Typeface")
    Spacer(Modifier.height(sp.xs))
    val fontRows = ReadingFont.entries.chunked(3)
    fontRows.forEach { rowFonts ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            rowFonts.forEach { font ->
                FontChip(
                    font = font,
                    selected = readingFont == font,
                    onClick = { onSetReadingFont(font) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(3 - rowFonts.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(sp.md))
    SliderRow(
        label = "Font size",
        value = textSize,
        valueLabel = "${textSize.toInt()}sp",
        valueRange = 14f..24f,
        onValueChange = onSetTextSize,
    )
    Spacer(Modifier.height(sp.sm))
    SliderRow(
        label = "Line height",
        value = lineHeightMultiplier,
        valueLabel = "×${"%.2f".format(lineHeightMultiplier)}",
        valueRange = 1.20f..2.00f,
        onValueChange = onSetLineHeight,
    )
    Spacer(Modifier.height(sp.sm))
    SliderRow(
        label = "Paragraph spacing",
        value = paragraphSpacing,
        valueLabel = "${paragraphSpacing.toInt()}dp",
        valueRange = 4f..48f,
        onValueChange = onSetParagraphSpacing,
    )
    Spacer(Modifier.height(sp.sm))
    SliderRow(
        label = "Letter spacing",
        value = letterSpacing,
        valueLabel = "${"%.2f".format(letterSpacing)}sp",
        valueRange = 0f..2f,
        onValueChange = onSetLetterSpacing,
    )
    Spacer(Modifier.height(sp.sm))
    SliderRow(
        label = "First-line indent",
        value = textIndent,
        valueLabel = "${textIndent.toInt()}sp",
        valueRange = 0f..48f,
        onValueChange = onSetTextIndent,
    )

    Spacer(Modifier.height(sp.sm))
    ToggleRow(
        label = "Justify text",
        sub = "Align both edges of each line",
        checked = justify,
        onToggle = { onSetJustify(!justify) },
    )
}

@Composable
private fun FontChip(
    font: ReadingFont,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radii.md))
            .background(if (selected) palette.ink else palette.bg2)
            .border(1.dp, if (selected) palette.ink else palette.edge, RoundedCornerShape(radii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = font.displayName,
            fontFamily = font.fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) palette.bg else palette.ink,
        )
    }
}

@Composable
fun ToggleRow(label: String, sub: String, checked: Boolean, onToggle: () -> Unit) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = palette.ink)
            Text(sub, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink3)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                uncheckedTrackColor = palette.bg3,
                checkedThumbColor = palette.bg,
                uncheckedThumbColor = palette.bg,
            ),
        )
    }
}
