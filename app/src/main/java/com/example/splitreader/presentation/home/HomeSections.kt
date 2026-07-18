package com.example.splitreader.presentation.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import com.example.splitreader.presentation.theme.FadeInOnAppear
import com.example.splitreader.presentation.theme.MotionTokens
import com.example.splitreader.presentation.theme.ShimmerBox
import com.example.splitreader.presentation.theme.StaggeredAppear
import com.example.splitreader.presentation.theme.animatedSelection
import com.example.splitreader.presentation.theme.pressScale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.example.splitreader.presentation.premium.PremiumViewModel
import com.example.splitreader.presentation.premium.PurchaseEventEffect
import com.example.splitreader.presentation.ui.LibraryLimitDialog
import com.example.splitreader.presentation.ui.LibraryTagButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs
// ── Library header ───────────────────────────────────────────────────────

@Composable
internal fun LibraryHeader(
    weeklyMinutes: Int,
    savedWords: Int,
    userName: String?,
    onOpenFilePicker: () -> Unit,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val today = LocalDate.now()
    val dayName = today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH).uppercase()
    val monthName = today.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase()
    val eyebrow = "$dayName · ${today.dayOfMonth} $monthName"
    val hour = java.time.LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    val greetingText = if (!userName.isNullOrBlank()) "$greeting, $userName" else greeting

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = sp.lg, bottom = sp.xs),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = greetingText,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                color = palette.ink,
            )
            if (weeklyMinutes > 0 || savedWords > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$weeklyMinutes minutes of reading this week · $savedWords words saved.",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = palette.ink2,
                )
            }
        }
        Spacer(Modifier.width(sp.md))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
            IconButton(onClick = onToggleSearch) {
                Crossfade(
                    targetState = searchActive,
                    animationSpec = tween(MotionTokens.Fast, easing = MotionTokens.EaseStandard),
                    label = "searchIcon",
                ) { active ->
                    Icon(
                        imageVector = if (active) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = if (active) "Close search" else "Search books",
                        tint = animatedSelection(if (active) palette.accent else palette.ink2, "searchIconTint"),
                    )
                }
            }
            LibraryTagButton(text = "Open book", onClick = onOpenFilePicker)
        }
    }
}

// ── Library search ───────────────────────────────────────────────────────

@Composable
internal fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val palette = LocalReaderPalette.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = sp.sm)
            .clip(RoundedCornerShape(radii.md))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
            .padding(horizontal = sp.md, vertical = sp.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search by title or author",
                    fontFamily = Newsreader,
                    fontSize = 16.sp,
                    color = palette.ink3,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Newsreader,
                    fontSize = 16.sp,
                    color = palette.ink,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(palette.accent),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear search",
                    tint = palette.ink3,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun NoBooksMatch(query: String) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sp.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No books match",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            color = palette.ink,
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Nothing found for “${query.trim()}”.",
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
                textAlign = TextAlign.Center,
            )
        }
    }
}


// ── Streak ribbon ────────────────────────────────────────────────────────

@Composable
internal fun StreakRibbon(streakDays: Int, weeklyMinutes: Int, weeklyGoal: Int) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val palette = LocalReaderPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.md))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
            .padding(horizontal = sp.md, vertical = sp.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        // Flame icon
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null,
                tint = palette.accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    text = if (streakDays > 0) "$streakDays-day streak" else "Start your streak",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = palette.ink,
                )
                if (streakDays > 0) {
                    Text(
                        text = " · keep it warm",
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        color = palette.ink3,
                    )
                }
            }
        }
        // 7-day bar
        StreakBar(streakDays = streakDays)
        // Minutes ratio
        Text(
            text = "$weeklyMinutes/${weeklyGoal}m",
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
    }
}

@Composable
private fun StreakBar(streakDays: Int) {
    val palette = LocalReaderPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val today = LocalDate.now()
        (6 downTo 0).forEach { daysAgo ->
            val active = daysAgo < streakDays
            val isToday = daysAgo == 0
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active || isToday) palette.accent else palette.bg3)
                    .then(
                        if (isToday) Modifier.border(1.dp, palette.accent, RoundedCornerShape(2.dp))
                        else Modifier
                    )
            )
        }
    }
}

// ── Continue reading hero ────────────────────────────────────────────────

/** Human-friendly "last opened" label: "Today, 8:42 PM" / "Yesterday, 8:42 PM" / "May 30". */
private fun formatLastOpened(millis: Long): String {
    if (millis <= 0L) return "—"
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val date = dateTime.toLocalDate()
    val today = LocalDate.now(zone)
    val time = dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    return when (date) {
        today -> "Today, $time"
        today.minusDays(1) -> "Yesterday, $time"
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    }
}

@Composable
internal fun ContinueReadingHero(book: BookItem, minutesToday: Int, onContinue: () -> Unit) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val palette = LocalReaderPalette.current
    val spec = coverSpec(book.title, book.uri)
    val progress = if (book.chapterCount > 0) book.lastChapterIndex.toFloat() / book.chapterCount else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.lg))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(radii.lg))
            .padding(sp.lg),
        horizontalArrangement = Arrangement.spacedBy(sp.md),
        verticalAlignment = Alignment.Top,
    ) {
        // Book cover
        BookCover(
            title = book.title,
            author = book.author,
            bgColor = spec.bg,
            inkColor = spec.ink,
            motif = spec.motif,
            width = 120.dp,
            height = 176.dp,
            coverFilePath = book.coverPath,
        )

        // Title block — at least the cover height so a short/absent synopsis still
        // pins the progress block to the bottom; grows (no clip) when the synopsis is tall.
        Column(modifier = Modifier.weight(1f).heightIn(min = 176.dp)) {
            Text(
                text = "CONTINUE READING",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.title,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                letterSpacing = (-0.3).sp,
                lineHeight = 32.sp,
                color = palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "by ${book.author}",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
            )
            // Prefer the passage the reader stopped on; fall back to the book's description.
            val excerpt = book.excerpt ?: book.synopsis
            if (excerpt != null) {
                Spacer(Modifier.height(sp.sm))
                Text(
                    text = excerpt,
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = palette.ink2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            // Progress bar
            ProgressRule(progress = progress, modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "CH ${book.lastChapterIndex + 1} OF ${book.chapterCount}",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
            }
        }

        // Right column
        Column(
            modifier = Modifier.width(190.dp),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            // Last opened card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .background(palette.bg3)
                    .padding(sp.sm),
            ) {
                Text(
                    text = "LAST OPENED",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatLastOpened(book.lastOpenedAt),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = palette.ink,
                )
                if (minutesToday > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "+$minutesToday MIN TODAY",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = palette.ink3,
                    )
                }
            }
            // Continue button — solid black tag, primary CTA in the "Open book" vocabulary
            LibraryTagButton(
                text = "Continue reading",
                onClick = onContinue,
                showPlus = false,
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Shelf header ─────────────────────────────────────────────────────────

@Composable
internal fun ShelfHeader(
    totalCount: Int,
    readingCount: Int,
    finishedCount: Int,
    unreadCount: Int,
    selectedFilter: Int,
    onFilterSelected: (Int) -> Unit,
) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val palette = LocalReaderPalette.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = sp.lg, bottom = sp.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                Text(
                    text = "Your shelf",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    color = palette.ink,
                )
                Text(
                    text = "· $totalCount VOLUMES",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                listOf("All · $totalCount", "Reading · $readingCount", "Finished · $finishedCount", "Unread · $unreadCount")
                    .forEachIndexed { index, label ->
                        FilterPill(
                            label = label,
                            selected = selectedFilter == index,
                            onClick = { onFilterSelected(index) },
                        )
                    }
            }
        }
        // Hairline divider
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        Spacer(Modifier.height(sp.sm))
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val radii = LocalRadii.current
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radii.md))
            .background(animatedSelection(if (selected) palette.ink else palette.bg, "filterPillBg"))
            .border(1.dp, animatedSelection(if (selected) palette.ink else palette.edge, "filterPillBorder"), RoundedCornerShape(radii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontFamily = if (selected) Newsreader else JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontSize = 11.sp,
            letterSpacing = if (selected) 0.sp else 0.3.sp,
            color = animatedSelection(if (selected) palette.bg else palette.ink2, "filterPillText"),
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────

@Composable
internal fun EmptyLibrary(onOpenFilePicker: () -> Unit) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        Icon(
            Icons.Outlined.MenuBook,
            contentDescription = null,
            tint = palette.ink4,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = "Your library is empty",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = palette.ink,
        )
        Text(
            text = "Tap “Open book” to add an EPUB or FB2.",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = palette.ink3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(sp.sm))
        LibraryTagButton(text = "Open book", onClick = onOpenFilePicker)
    }
}

