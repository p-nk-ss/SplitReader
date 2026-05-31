package com.example.splitreader.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.splitreader.presentation.ui.BrandIcon

@Composable
fun AppShell(
    currentRoute: String?,
    onNavigateToHome: () -> Unit,
    onNavigateToAlmanac: () -> Unit,
    onNavigateToWords: () -> Unit,
    onNavigateToSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isReader = currentRoute?.startsWith("reader") == true
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        // App status strip
        AppStatusStrip()

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (!isReader) {
                EditorialNavigationRail(
                    currentRoute = currentRoute,
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToAlmanac = onNavigateToAlmanac,
                    onNavigateToWords = onNavigateToWords,
                    onNavigateToSettings = onNavigateToSettings,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    }
}

@Composable
private fun AppStatusStrip() {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalSpacing.current.statusBar)
            .background(palette.bg)
            .drawBehind {
                drawLine(
                    color = palette.edge,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = LocalSpacing.current.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SPLIT READER",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "ML KIT READY",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun EditorialNavigationRail(
    currentRoute: String?,
    onNavigateToHome: () -> Unit,
    onNavigateToAlmanac: () -> Unit,
    onNavigateToWords: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge

    Column(
        modifier = Modifier
            .width(sp.railWidth)
            .fillMaxHeight()
            .background(palette.bg2)
            .drawBehind {
                drawLine(
                    color = edgeColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = sp.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wordmark
        RailWordmark()

        Spacer(Modifier.height(sp.sm))

        // 1 px separator
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.edge)
        )

        Spacer(Modifier.height(sp.sm))

        // Navigation tabs
        RailTab(
            icon = Icons.Outlined.MenuBook,
            label = "Library",
            selected = currentRoute == HOME_ROUTE,
            onClick = onNavigateToHome,
        )
        RailTab(
            icon = Icons.Outlined.BarChart,
            label = "Almanac",
            selected = currentRoute == ALMANAC_ROUTE,
            onClick = onNavigateToAlmanac,
        )
        RailTab(
            icon = Icons.Outlined.StickyNote2,
            label = "Words",
            selected = currentRoute == WORDS_ROUTE,
            onClick = onNavigateToWords,
        )

        Spacer(Modifier.weight(1f))

        // Settings
        RailTab(
            icon = Icons.Outlined.Settings,
            label = "Settings",
            selected = currentRoute == SETTINGS_ROUTE,
            onClick = onNavigateToSettings,
        )

        Spacer(Modifier.height(sp.sm))

        // Avatar
        RailAvatar()
    }
}

@Composable
private fun RailWordmark() {
    // On dark themes use the inverted (cream) brand mark so the wordmark stays
    // legible against the dark rail; the default ink tile is used on light themes.
    val palette = LocalReaderPalette.current
    BrandIcon(modifier = Modifier.size(44.dp), inverted = palette.isDark)
}

@Composable
private fun RailTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val accentColor = palette.accent

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .background(if (selected) palette.bg3 else palette.bg2)
            .drawBehind {
                if (selected) {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height * 0.2f),
                        end = Offset(0f, size.height * 0.8f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) palette.ink else palette.ink3,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                color = if (selected) palette.ink else palette.ink3,
            )
        }
    }
}

@Composable
private fun RailAvatar() {
    val palette = LocalReaderPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "A",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.bg,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "L1·A2",
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
    }
}
