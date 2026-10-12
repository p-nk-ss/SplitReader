package com.example.splitreader.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.domain.model.ReaderThemeKey
import com.example.splitreader.presentation.theme.AmoledPalette
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.NightPalette
import com.example.splitreader.presentation.theme.PaperPalette
import com.example.splitreader.presentation.theme.SepiaPalette
import com.example.splitreader.presentation.theme.animatedSelection

/**
 * The four reader-theme swatches ("Aa" + name, drawn in each theme's own colors), shared by the
 * reader's in-session Display dialog and the global Settings screen.
 */
@Composable
fun ThemeSwatchRow(selected: ReaderThemeKey, onSelect: (ReaderThemeKey) -> Unit) {
    val radii = LocalRadii.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(PaperPalette, SepiaPalette, NightPalette, AmoledPalette).forEach { p ->
            val isSelected = selected == p.key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(radii.md))
                    .background(p.bg)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        animatedSelection(if (isSelected) p.ink else p.edge, "themeSwatchBorder"),
                        RoundedCornerShape(radii.md),
                    )
                    .clickable { onSelect(p.key) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aa", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink)
                    Text(p.displayName.uppercase(), fontFamily = JetBrainsMono, fontSize = 11.sp, color = p.ink2)
                }
            }
        }
    }
}
