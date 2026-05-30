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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.data.local.DailyMinutes
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.PaperAccent
import com.example.splitreader.presentation.theme.PaperAccentSoft
import com.example.splitreader.presentation.theme.PaperBg
import com.example.splitreader.presentation.theme.PaperBg2
import com.example.splitreader.presentation.theme.PaperBg3
import com.example.splitreader.presentation.theme.PaperEdge
import com.example.splitreader.presentation.theme.PaperInk
import com.example.splitreader.presentation.theme.PaperInk2
import com.example.splitreader.presentation.theme.PaperInk3
import com.example.splitreader.presentation.theme.PaperInk4
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
    val weeklyMinutes by viewModel.weeklyMinutes.collectAsStateWithLifecycle()
    val weeklyPages by viewModel.weeklyPages.collectAsStateWithLifecycle()
    val weeklyWords by viewModel.weeklyWords.collectAsStateWithLifecycle()
    val timeByBook by viewModel.timeByBook.collectAsStateWithLifecycle()
    val timeByLang by viewModel.timeByLang.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperBg)
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
                Text("READING", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = PaperInk3)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your ", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = PaperInk)
                    Text("almanac", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = PaperAccent)
                }
            }
            // Range selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(radii.md))
                    .background(PaperBg2)
                    .border(1.dp, PaperEdge, RoundedCornerShape(radii.md))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TimeRange.entries.forEach { range ->
                    val selected = selectedRange == range
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) PaperInk else PaperBg2)
                            .clickable { viewModel.selectRange(range) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(range.label, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                            color = if (selected) PaperBg else PaperInk2)
                    }
                }
            }
        }

        val hasData = streak.current > 0 || weeklyMinutes > 0

        if (!hasData) {
            // Empty state
            EmptyAlmanac()
        } else {
            // Top row: streak hero + 3 stat blocks
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                StreakHeroCard(streak.current, streak.longest, modifier = Modifier.weight(1.4f))
                StatBlock("$weeklyMinutes", "minutes", "of 180 this week", Modifier.weight(1f))
                StatBlock("$weeklyPages", "pages", "this week", Modifier.weight(1f))
                StatBlock("$weeklyWords", "words", "saved this week", Modifier.weight(1f))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalRadii.current.lg))
            .background(PaperBg2)
            .border(1.dp, PaperEdge, RoundedCornerShape(LocalRadii.current.lg))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BEGIN", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = PaperInk3)
            Text("Open a book to start tracking your reading.", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = PaperInk)
            Text("Your streak, minutes, and word counts will appear here.", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = PaperInk3)
        }
    }
}

@Composable
private fun AlmanacCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(LocalRadii.current.lg))
            .background(PaperBg2)
            .border(1.dp, PaperEdge, RoundedCornerShape(LocalRadii.current.lg))
            .padding(14.dp, 18.dp),
    ) { content() }
}

@Composable
private fun StreakHeroCard(current: Int, longest: Int, modifier: Modifier = Modifier) {
    AlmanacCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(PaperAccentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = PaperAccent, modifier = Modifier.size(32.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$current", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 32.sp, letterSpacing = (-0.5).sp, color = PaperInk)
                    Text("day streak", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = PaperInk2)
                }
                Row {
                    Text("Longest: ", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = PaperInk3)
                    Text("$longest days", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = PaperInk2)
                    Text(" · keep going to beat it.", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = PaperInk3)
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, unit: String, sub: String, modifier: Modifier = Modifier) {
    AlmanacCard(modifier) {
        Text(value, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 44.sp, letterSpacing = (-0.6).sp, lineHeight = 48.sp, color = PaperInk)
        Spacer(Modifier.height(6.dp))
        Text(unit, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = PaperInk2)
        Text(sub, fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.3.sp, color = PaperInk3)
    }
}

@Composable
private fun WeeklyBarChartCard(days: List<DailyMinutes>, modifier: Modifier = Modifier) {
    AlmanacCard(modifier) {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dayLabels = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val maxMinutes = days.maxOfOrNull { it.minutes } ?: 1

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("THIS WEEK", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = PaperInk3)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            dayLabels.forEach { date ->
                val dayStr = date.format(fmt)
                val mins = days.find { it.day == dayStr }?.minutes ?: 0
                val isToday = date == today
                val barH = if (maxMinutes > 0) (mins.toFloat() / maxMinutes * 100).coerceAtLeast(3f) else 3f
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (mins > 0) Text("${mins}m", fontFamily = JetBrainsMono, fontSize = 8.sp, color = PaperInk3)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barH.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(when {
                                isToday -> PaperAccent
                                mins > 0 -> PaperInk2
                                else -> PaperBg3
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
                    fontSize = 9.sp,
                    color = if (isToday) PaperAccent else PaperInk3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(days: List<DailyMinutes>, modifier: Modifier = Modifier) {
    AlmanacCard(modifier) {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val sinceDay = today.minusWeeks(26)
        val minuteMap = days.associate { it.day to it.minutes }
        val activeDays = days.count { it.minutes > 0 }
        val totalMins = days.sumOf { it.minutes }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LAST 26 WEEKS", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = PaperInk3)
            Text("$activeDays ACTIVE DAYS · ${totalMins} MIN", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.3.sp, color = PaperInk3)
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
                        0 -> PaperBg3
                        1 -> PaperAccentSoft
                        2 -> PaperAccent.copy(alpha = 0.6f)
                        3 -> PaperAccent.copy(alpha = 0.85f)
                        else -> PaperInk
                    }
                    val x = col * (cellW + gap)
                    val y = row * (cellH + gap)
                    drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(cellW, cellH), cornerRadius = CornerRadius(radius))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("LESS", fontFamily = JetBrainsMono, fontSize = 8.sp, color = PaperInk4)
            Spacer(Modifier.width(4.dp))
            listOf(PaperBg3, PaperAccentSoft, PaperAccent.copy(alpha = 0.6f), PaperAccent.copy(alpha = 0.85f), PaperInk).forEach { color ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(2.dp))
            }
            Text("MORE", fontFamily = JetBrainsMono, fontSize = 8.sp, color = PaperInk4)
        }
    }
}

@Composable
private fun TimeByBookCard(books: List<com.example.splitreader.data.local.BookMinutes>, modifier: Modifier = Modifier) {
    AlmanacCard(modifier) {
        Text("TIME BY BOOK", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = PaperInk3)
        Spacer(Modifier.height(8.dp))
        val maxMins = books.maxOfOrNull { it.minutes } ?: 1
        books.forEach { book ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(book.title, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = PaperInk,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${book.minutes}M", fontFamily = JetBrainsMono, fontSize = 9.sp, color = PaperInk3)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).clip(RoundedCornerShape(1.dp)).background(PaperBg3)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(book.minutes.toFloat() / maxMins).background(PaperAccent))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (books.isEmpty()) {
            Text("No reading sessions yet.", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = PaperInk3)
        }
    }
}

@Composable
private fun LanguagesCard(langs: List<com.example.splitreader.data.local.LangMinutes>, modifier: Modifier = Modifier) {
    AlmanacCard(modifier) {
        Text("LANGUAGES", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.5.sp, color = PaperInk3)
        Spacer(Modifier.height(8.dp))
        langs.forEach { lang ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    Modifier.clip(RoundedCornerShape(3.dp)).background(PaperBg3).padding(4.dp, 2.dp),
                ) {
                    Text(lang.lang.uppercase(), fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = PaperInk)
                }
                Text("${lang.minutes} MIN", fontFamily = JetBrainsMono, fontSize = 9.sp, color = PaperInk3)
            }
            Spacer(Modifier.height(6.dp))
        }
        if (langs.isEmpty()) {
            Text("No data yet.", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = PaperInk3)
        }
    }
}
