package com.example.splitreader.presentation.almanac

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.R
import com.example.splitreader.data.local.DailyMinutes
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.FadeInOnAppear
import com.example.splitreader.presentation.theme.animatedSelection
import com.example.splitreader.presentation.theme.Newsreader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlmanacRoute(viewModel: AlmanacViewModel = hiltViewModel()) {
    AlmanacScreen(viewModel = viewModel)
}

@Composable
fun AlmanacScreen(viewModel: AlmanacViewModel = hiltViewModel()) {
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val dailyMinutes by viewModel.dailyMinutes.collectAsStateWithLifecycle()
    val rangeMinutes by viewModel.rangeMinutes.collectAsStateWithLifecycle()
    val rangePages by viewModel.rangePages.collectAsStateWithLifecycle()
    val rangeWords by viewModel.rangeWords.collectAsStateWithLifecycle()
    val timeByBook by viewModel.timeByBook.collectAsStateWithLifecycle()
    val timeByLang by viewModel.timeByLang.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val palette = LocalReaderPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = sp.xxl, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("READING", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your ", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = palette.ink)
                    Text("almanac", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = palette.accent)
                }
            }
            // Range selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(radii.md))
                    .background(palette.bg2)
                    .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TimeRange.entries.forEach { range ->
                    val selected = selectedRange == range
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(animatedSelection(if (selected) palette.ink else palette.bg2, "rangeBg"))
                            .clickable { viewModel.selectRange(range) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(range.label, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                            color = animatedSelection(if (selected) palette.bg else palette.ink2, "rangeText"))
                    }
                }
            }
        }

        val hasData = streak.current > 0 || rangeMinutes > 0

        // Captions track the selected range: "this week/month/year" or "all time".
        val periodLabel = if (selectedRange == TimeRange.ALL) "all time"
            else "this ${selectedRange.label.lowercase()}"
        // The 180-minute goal is a weekly target, so only surface it for the Week range.
        val minutesCaption = if (selectedRange == TimeRange.WEEK) "of 180 this week" else periodLabel

        if (!hasData) {
            // Empty state
            FadeInOnAppear { EmptyAlmanac() }
        } else {
            // Top row: streak hero + 3 stat blocks
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                StreakHeroCard(streak.current, streak.longest, modifier = Modifier.weight(1.4f))
                StatBlock("$rangeMinutes", "minutes", minutesCaption, Modifier.weight(1f))
                StatBlock("$rangePages", "pages", periodLabel, Modifier.weight(1f))
                StatBlock("$rangeWords", "words", "saved $periodLabel", Modifier.weight(1f))
            }

            // Middle row: weekly bar chart + 26-week heatmap
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                WeeklyBarChartCard(dailyMinutes.takeLast(7), modifier = Modifier.weight(1f))
                HeatmapCard(dailyMinutes, modifier = Modifier.weight(1.6f))
            }

            // Bottom row: time by book + languages
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                TimeByBookCard(timeByBook, modifier = Modifier.weight(1.5f))
                LanguagesCard(timeByLang, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyAlmanac() {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalRadii.current.lg))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(LocalRadii.current.lg))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BEGIN", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
            Text(stringResource(R.string.almanac_empty_title), fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = palette.ink)
            Text(stringResource(R.string.almanac_empty_body), fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = palette.ink3)
        }
    }
}

@Composable
private fun AlmanacCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(LocalRadii.current.lg))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(LocalRadii.current.lg))
            .padding(14.dp, 18.dp),
    ) { content() }
}

@Composable
private fun StreakHeroCard(current: Int, longest: Int, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    AlmanacCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = palette.accent, modifier = Modifier.size(32.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$current", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 32.sp, letterSpacing = (-0.5).sp, color = palette.ink)
                    Text("day streak", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = palette.ink2)
                }
                Row {
                    Text("Longest: ", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink3)
                    Text("$longest days", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink2)
                    Text(stringResource(R.string.almanac_streak_encourage), fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink3)
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, unit: String, sub: String, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    AlmanacCard(modifier) {
        Text(value, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 44.sp, letterSpacing = (-0.6).sp, lineHeight = 48.sp, color = palette.ink)
        Spacer(Modifier.height(6.dp))
        Text(unit, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = palette.ink2)
        Text(sub, fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.3.sp, color = palette.ink3)
    }
}

@Composable
private fun WeeklyBarChartCard(days: List<DailyMinutes>, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    AlmanacCard(modifier) {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dayLabels = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val maxMinutes = days.maxOfOrNull { it.minutes } ?: 1

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("THIS WEEK", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            dayLabels.forEach { date ->
                val dayStr = date.format(fmt)
                val mins = days.find { it.day == dayStr }?.minutes ?: 0
                val isToday = date == today
                val barH = if (maxMinutes > 0) (mins.toFloat() / maxMinutes * 100).coerceAtLeast(3f) else 3f
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (mins > 0) Text("${mins}m", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barH.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(when {
                                isToday -> palette.accent
                                mins > 0 -> palette.ink2
                                else -> palette.bg3
                            })
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            dayLabels.forEach { date ->
                val isToday = date == today
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    modifier = Modifier.weight(1f),
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = if (isToday) palette.accent else palette.ink3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(days: List<DailyMinutes>, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    AlmanacCard(modifier) {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val sinceDay = today.minusWeeks(26)
        val minuteMap = days.associate { it.day to it.minutes }
        val activeDays = days.count { it.minutes > 0 }
        val totalMins = days.sumOf { it.minutes }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LAST 26 WEEKS", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
            Text(stringResource(R.string.almanac_heatmap_summary, activeDays, totalMins), fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.3.sp, color = palette.ink3)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val cols = 26
            val rows = 7
            val gap = 4.dp.toPx()
            val cellW = (size.width - gap * (cols - 1)) / cols
            val cellH = (size.height - gap * (rows - 1)) / rows
            val radius = 3.dp.toPx()

            for (col in 0 until cols) {
                for (row in 0 until rows) {
                    val weekStart = sinceDay.plusWeeks(col.toLong())
                    val day = weekStart.plusDays(row.toLong())
                    if (day.isAfter(today)) continue
                    val dayStr = day.format(fmt)
                    val mins = minuteMap[dayStr] ?: 0
                    val level = when {
                        mins == 0 -> 0
                        mins <= 10 -> 1
                        mins <= 25 -> 2
                        mins <= 60 -> 3
                        else -> 4
                    }
                    val color = when (level) {
                        0 -> palette.bg3
                        1 -> palette.accentSoft
                        2 -> palette.accent.copy(alpha = 0.6f)
                        3 -> palette.accent.copy(alpha = 0.85f)
                        else -> palette.ink
                    }
                    val x = col * (cellW + gap)
                    val y = row * (cellH + gap)
                    drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(cellW, cellH), cornerRadius = CornerRadius(radius))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("LESS", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink4)
            Spacer(Modifier.width(4.dp))
            listOf(palette.bg3, palette.accentSoft, palette.accent.copy(alpha = 0.6f), palette.accent.copy(alpha = 0.85f), palette.ink).forEach { color ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(2.dp))
            }
            Text("MORE", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink4)
        }
    }
}

@Composable
private fun TimeByBookCard(books: List<com.example.splitreader.data.local.BookMinutes>, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    AlmanacCard(modifier) {
        Text("TIME BY BOOK", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
        Spacer(Modifier.height(8.dp))
        val maxMins = books.maxOfOrNull { it.minutes } ?: 1
        books.forEach { book ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(book.title, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.ink,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${book.minutes}M", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).clip(RoundedCornerShape(1.dp)).background(palette.bg3)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(book.minutes.toFloat() / maxMins).background(palette.accent))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (books.isEmpty()) {
            Text(stringResource(R.string.almanac_no_sessions), fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = palette.ink3)
        }
    }
}

@Composable
private fun LanguagesCard(langs: List<com.example.splitreader.data.local.LangMinutes>, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    AlmanacCard(modifier) {
        Text("LANGUAGES", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
        Spacer(Modifier.height(8.dp))
        langs.forEach { lang ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    Modifier.clip(RoundedCornerShape(3.dp)).background(palette.bg3).padding(4.dp, 2.dp),
                ) {
                    Text(lang.lang.uppercase(), fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = palette.ink)
                }
                Text("${lang.minutes} MIN", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)
            }
            Spacer(Modifier.height(6.dp))
        }
        if (langs.isEmpty()) {
            Text(stringResource(R.string.almanac_no_data), fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = palette.ink3)
        }
    }
}
