package com.example.splitreader.presentation.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.splitreader.presentation.theme.PaperMoss
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs

// ── Cover motifs ────────────────────────────────────────────────────────
enum class CoverMotif { LEAF, CATHEDRAL, STRIPE, DOTS, SUN, CIRCLE, HORIZON }

data class CoverSpec(val bg: Color, val ink: Color, val motif: CoverMotif)

private val KNOWN_COVERS = mapOf(
    "madame bovary"         to CoverSpec(Color(0xFF5C3320), Color(0xFFF0DBB4), CoverMotif.LEAF),
    "notre-dame de paris"   to CoverSpec(Color(0xFF3C4663), Color(0xFFE5DAB8), CoverMotif.CATHEDRAL),
    "le rouge et le noir"   to CoverSpec(Color(0xFF7B2F1A), Color(0xFFF4E8BC), CoverMotif.STRIPE),
    "le père goriot"        to CoverSpec(Color(0xFFC5A763), Color(0xFF3A2C1E), CoverMotif.CIRCLE),
    "du côté de chez swann" to CoverSpec(Color(0xFF604163), Color(0xFFEFDAB6), CoverMotif.HORIZON),
    "bel-ami"               to CoverSpec(Color(0xFF3C7E76), Color(0xFFF1DEB9), CoverMotif.DOTS),
    "germinal"              to CoverSpec(Color(0xFF1E1A12), Color(0xFFC49645), CoverMotif.SUN),
    "l'étranger"            to CoverSpec(Color(0xFFEDE3CB), Color(0xFF34281A), CoverMotif.HORIZON),
)

private val FALLBACK_BG_COLORS = listOf(
    Color(0xFF5C3320), Color(0xFF3C4663), Color(0xFF7B2F1A), Color(0xFFC5A763),
    Color(0xFF604163), Color(0xFF3C7E76), Color(0xFF1E1A12), Color(0xFF4A3828),
)
private val FALLBACK_INK_COLORS = listOf(
    Color(0xFFF0DBB4), Color(0xFFE5DAB8), Color(0xFFF4E8BC), Color(0xFF3A2C1E),
    Color(0xFFEFDAB6), Color(0xFFF1DEB9), Color(0xFFC49645), Color(0xFFE8D5B5),
)
private val FALLBACK_MOTIFS = CoverMotif.entries.toList()

private fun coverSpec(title: String, uri: String): CoverSpec {
    val key = title.lowercase().trim()
    KNOWN_COVERS[key]?.let { return it }
    val hash = abs(uri.hashCode())
    return CoverSpec(
        bg = FALLBACK_BG_COLORS[hash % FALLBACK_BG_COLORS.size],
        ink = FALLBACK_INK_COLORS[hash % FALLBACK_INK_COLORS.size],
        motif = FALLBACK_MOTIFS[hash % FALLBACK_MOTIFS.size],
    )
}

// ── Entry point ─────────────────────────────────────────────────────────

@Composable
internal fun HomeRoute(
    onNavigateToReader: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openBook(it) } }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { path -> onNavigateToReader(path) }
    }

    HomeScreen(
        uiState = uiState,
        onOpenFilePicker = { fileLauncher.launch(arrayOf("*/*")) },
        onNavigateToReader = onNavigateToReader,
        onDeleteBook = viewModel::deleteBook,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onOpenFilePicker: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val sp = LocalSpacing.current
    var shelfFilter by remember { mutableIntStateOf(0) } // 0=All,1=Reading,2=Finished,3=Unread

    val filteredBooks = remember(uiState.books, shelfFilter) {
        when (shelfFilter) {
            1 -> uiState.books.filter { it.lastChapterIndex > 0 }
            2 -> uiState.books.filter { it.lastChapterIndex >= it.chapterCount - 1 && it.chapterCount > 0 }
            3 -> uiState.books.filter { it.lastChapterIndex == 0 }
            else -> uiState.books
        }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().background(PaperBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PaperAccent)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxSize().background(PaperBg),
        contentPadding = PaddingValues(sp.xxl),
        verticalArrangement = Arrangement.spacedBy(sp.lg),
        horizontalArrangement = Arrangement.spacedBy(sp.lg),
    ) {
        // Header — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(
                weeklyMinutes = 0,
                savedWords = 0,
                onOpenFilePicker = onOpenFilePicker,
            )
        }

        // Streak ribbon — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            StreakRibbon(streakDays = 0, weeklyMinutes = 0, weeklyGoal = 180)
        }

        // Continue reading hero — full width, only when there are books
        if (uiState.lastBook != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingHero(
                    book = uiState.lastBook!!,
                    onContinue = { onNavigateToReader(uiState.lastBook!!.uri) },
                )
            }
        }

        // Shelf header — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShelfHeader(
                totalCount = uiState.books.size,
                readingCount = uiState.books.count { it.lastChapterIndex > 0 },
                finishedCount = uiState.books.count {
                    it.lastChapterIndex >= it.chapterCount - 1 && it.chapterCount > 0
                },
                unreadCount = uiState.books.count { it.lastChapterIndex == 0 },
                selectedFilter = shelfFilter,
                onFilterSelected = { shelfFilter = it },
            )
        }

        if (filteredBooks.isEmpty() && uiState.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyLibrary(onOpenFilePicker = onOpenFilePicker)
            }
        } else {
            items(filteredBooks) { book ->
                BookCoverCard(
                    book = book,
                    onClick = { onNavigateToReader(book.uri) },
                )
            }
        }
    }
}

// ── Library header ───────────────────────────────────────────────────────

@Composable
private fun LibraryHeader(
    weeklyMinutes: Int,
    savedWords: Int,
    onOpenFilePicker: () -> Unit,
) {
    val sp = LocalSpacing.current
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
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = PaperInk3,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = "$greeting, ",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 28.sp,
                    color = PaperInk,
                )
                Text(
                    text = "Анна.",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = PaperAccent,
                )
            }
            if (weeklyMinutes > 0 || savedWords > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$weeklyMinutes minutes of reading this week · $savedWords words saved.",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = PaperInk2,
                )
            }
        }
        Spacer(Modifier.width(sp.md))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = PaperInk2)
            }
            OpenBookButton(onClick = onOpenFilePicker)
        }
    }
}

@Composable
private fun OpenBookButton(onClick: () -> Unit) {
    val radii = LocalRadii.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radii.sm))
            .background(PaperInk)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Open book",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = PaperBg,
        )
    }
}

// ── Streak ribbon ────────────────────────────────────────────────────────

@Composable
private fun StreakRibbon(streakDays: Int, weeklyMinutes: Int, weeklyGoal: Int) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.md))
            .background(PaperBg2)
            .border(1.dp, PaperEdge, RoundedCornerShape(radii.md))
            .padding(horizontal = sp.md, vertical = sp.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        // Flame icon
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(PaperAccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null,
                tint = PaperAccent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    text = if (streakDays > 0) "$streakDays-day streak" else "Start your streak",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = PaperInk,
                )
                if (streakDays > 0) {
                    Text(
                        text = " · keep it warm",
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        color = PaperInk3,
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
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = PaperInk3,
        )
    }
}

@Composable
private fun StreakBar(streakDays: Int) {
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
                    .background(if (active || isToday) PaperAccent else PaperBg3)
                    .then(
                        if (isToday) Modifier.border(1.dp, PaperAccent, RoundedCornerShape(2.dp))
                        else Modifier
                    )
            )
        }
    }
}

// ── Continue reading hero ────────────────────────────────────────────────

@Composable
private fun ContinueReadingHero(book: BookItem, onContinue: () -> Unit) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val spec = coverSpec(book.title, book.uri)
    val progress = if (book.chapterCount > 0) book.lastChapterIndex.toFloat() / book.chapterCount else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.lg))
            .background(PaperBg2)
            .border(1.dp, PaperEdge, RoundedCornerShape(radii.lg))
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
        )

        // Title block
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CONTINUE READING",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = PaperAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.title,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                letterSpacing = (-0.3).sp,
                lineHeight = 32.sp,
                color = PaperInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "by ${book.author}",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = PaperInk2,
            )
            Spacer(Modifier.height(sp.md))
            // Progress bar
            ProgressRule(progress = progress, modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "CH ${book.lastChapterIndex + 1} · PAGE —/${book.chapterCount}",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = PaperInk3,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = PaperInk3,
                )
            }
        }

        // Right column
        Column(
            modifier = Modifier.width(160.dp),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            // Last opened card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .background(PaperBg3)
                    .padding(sp.sm),
            ) {
                Text(
                    text = "LAST OPENED",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
                    color = PaperInk3,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Today",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = PaperInk,
                )
            }
            // Continue button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .background(PaperInk)
                    .clickable(onClick = onContinue)
                    .padding(sp.sm),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Continue",
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        color = PaperBg,
                    )
                    Icon(
                        Icons.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = PaperBg,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ── Shelf header ─────────────────────────────────────────────────────────

@Composable
private fun ShelfHeader(
    totalCount: Int,
    readingCount: Int,
    finishedCount: Int,
    unreadCount: Int,
    selectedFilter: Int,
    onFilterSelected: (Int) -> Unit,
) {
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

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
                    color = PaperInk,
                )
                Text(
                    text = "· $totalCount VOLUMES",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    color = PaperInk3,
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(PaperEdge))
        Spacer(Modifier.height(sp.sm))
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val radii = LocalRadii.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radii.md))
            .background(if (selected) PaperInk else PaperBg)
            .border(1.dp, if (selected) PaperInk else PaperEdge, RoundedCornerShape(radii.md))
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
            color = if (selected) PaperBg else PaperInk2,
        )
    }
}

// ── Book grid card ────────────────────────────────────────────────────────

@Composable
private fun BookCoverCard(book: BookItem, onClick: () -> Unit) {
    val spec = coverSpec(book.title, book.uri)
    val progress = if (book.chapterCount > 0) book.lastChapterIndex.toFloat() / book.chapterCount else 0f
    val finished = book.lastChapterIndex >= book.chapterCount - 1 && book.chapterCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box {
            BookCover(
                title = book.title,
                author = book.author,
                bgColor = spec.bg,
                inkColor = spec.ink,
                motif = spec.motif,
                width = 140.dp,
                height = 206.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            // Progress overlay at bottom
            if (progress > 0f && !finished) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(PaperBg2.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(PaperAccent)
                    )
                }
            }
            // Finished badge
            if (finished) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(PaperMoss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = PaperBg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.title,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = PaperInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "by ${book.author}",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
            color = PaperInk3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (progress > 0f) {
            Text(
                text = "${(progress * 100).toInt()}% · CH ${book.lastChapterIndex + 1}/${book.chapterCount}",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Normal,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp,
                color = PaperInk3,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────

@Composable
private fun EmptyLibrary(onOpenFilePicker: () -> Unit) {
    val sp = LocalSpacing.current
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
            tint = PaperInk4,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = "Your library is empty",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = PaperInk,
        )
        Text(
            text = "Tap “Open book” to add an EPUB or FB2.",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = PaperInk3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(sp.sm))
        OpenBookButton(onClick = onOpenFilePicker)
    }
}

// ── BookCover composable ──────────────────────────────────────────────────

@Composable
fun BookCover(
    title: String,
    author: String,
    bgColor: Color,
    inkColor: Color,
    motif: CoverMotif,
    width: Dp,
    height: Dp,
    radius: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(bgColor),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Spine highlight gradient
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        inkColor.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                    startX = 0f,
                    endX = w * 0.3f,
                ),
                size = size,
            )

            // Top hairlines
            val hairColor = inkColor.copy(alpha = 0.32f)
            val sideMargin = 8.dp.toPx()
            drawLine(hairColor, Offset(sideMargin, 14.dp.toPx()), Offset(w - sideMargin, 14.dp.toPx()), 1.dp.toPx())
            drawLine(inkColor.copy(alpha = 0.18f), Offset(sideMargin, 18.dp.toPx()), Offset(w - sideMargin, 18.dp.toPx()), 1.dp.toPx())

            // Motif
            val motifTop = 26.dp.toPx()
            val motifHeight = 64.dp.toPx()
            drawMotif(motif, inkColor, w, motifTop, motifHeight)
        }

        // Title / author overlay at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text(
                text = title.uppercase(),
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontSize = (width.value * 0.115f).sp,
                color = inkColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (width.value * 0.115f * 1.2f).sp,
            )
            Text(
                text = author,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = (width.value * 0.08f).sp,
                color = inkColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun DrawScope.drawMotif(motif: CoverMotif, color: Color, w: Float, topY: Float, maxH: Float) {
    val cx = w / 2f
    val stroke = Stroke(width = 1.5.dp.toPx())

    when (motif) {
        CoverMotif.LEAF -> {
            // Vertical ellipse outline with central stem
            val rx = maxH * 0.25f
            val ry = maxH * 0.5f
            drawOval(color = color, topLeft = Offset(cx - rx, topY), size = Size(rx * 2, ry * 2), style = stroke)
            drawLine(color, Offset(cx, topY), Offset(cx, topY + ry * 2), stroke.width)
        }
        CoverMotif.CATHEDRAL -> {
            // Rectangle + rounded arch on top, central vertical line
            val bW = maxH * 0.5f
            val bH = maxH * 0.5f
            val archH = maxH * 0.3f
            val left = cx - bW / 2
            val rectTop = topY + archH * 0.8f
            drawRect(color, Offset(left, rectTop), Size(bW, bH), style = stroke)
            val path = Path().apply {
                moveTo(left, rectTop)
                lineTo(left, topY + archH)
                quadraticTo(cx, topY, cx + bW / 2, topY + archH)
                lineTo(cx + bW / 2, rectTop)
            }
            drawPath(path, color, style = stroke)
            drawLine(color, Offset(cx, rectTop), Offset(cx, rectTop + bH), stroke.width)
        }
        CoverMotif.STRIPE -> {
            // 4 fading vertical bars
            val barW = maxH * 0.07f
            val barH = maxH * 0.75f
            val gap = maxH * 0.12f
            val totalW = 4 * barW + 3 * gap
            var x = cx - totalW / 2
            repeat(4) { i ->
                val alpha = 1f - i * 0.2f
                drawRect(color.copy(alpha = alpha), Offset(x, topY), Size(barW, barH))
                x += barW + gap
            }
        }
        CoverMotif.DOTS -> {
            // 5 circles in a row
            val r = maxH * 0.07f
            val spacing = maxH * 0.2f
            val totalW = 5 * r * 2 + 4 * spacing
            var x = cx - totalW / 2 + r
            val cy = topY + maxH * 0.3f
            repeat(5) {
                drawCircle(color, radius = r, center = Offset(x, cy), style = stroke)
                x += r * 2 + spacing
            }
        }
        CoverMotif.SUN -> {
            // Filled circle + horizontal rule
            val r = maxH * 0.2f
            val cy = topY + maxH * 0.35f
            drawCircle(color, radius = r, center = Offset(cx, cy))
            drawLine(color, Offset(cx - maxH * 0.4f, cy + r + 8.dp.toPx()), Offset(cx + maxH * 0.4f, cy + r + 8.dp.toPx()), stroke.width)
        }
        CoverMotif.CIRCLE -> {
            // Two concentric outlined circles
            val r1 = maxH * 0.35f
            val r2 = maxH * 0.22f
            val cy = topY + maxH * 0.4f
            drawCircle(color, radius = r1, center = Offset(cx, cy), style = stroke)
            drawCircle(color, radius = r2, center = Offset(cx, cy), style = stroke)
        }
        CoverMotif.HORIZON -> {
            // Horizontal rule + small circle above it
            val lineY = topY + maxH * 0.5f
            drawLine(color, Offset(cx - maxH * 0.4f, lineY), Offset(cx + maxH * 0.4f, lineY), stroke.width)
            val r = maxH * 0.12f
            drawCircle(color, radius = r, center = Offset(cx, lineY - r * 2 - 4.dp.toPx()), style = stroke)
        }
    }
}

// ── Progress rule ─────────────────────────────────────────────────────────

@Composable
fun ProgressRule(progress: Float, modifier: Modifier = Modifier) {
    val radii = LocalRadii.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(PaperBg3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(PaperAccent)
        )
    }
}
