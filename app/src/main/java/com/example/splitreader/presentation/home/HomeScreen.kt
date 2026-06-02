package com.example.splitreader.presentation.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.splitreader.presentation.theme.animatedSelection
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
import com.example.splitreader.presentation.ui.BookplateButton
import com.example.splitreader.presentation.ui.LibraryTagButton
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

// TODO(architecture): this file (~1k lines) mixes the library grid, header/streak ribbon, the
//  continue-reading hero, book-cover rendering, and cover-motif drawing. Consider extracting the
//  cover rendering/motifs and the header/streak section into separate files. No behavior change.
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
        onOpenFromLibrary = viewModel::openBookFromLibrary,
        onDeleteBook = viewModel::deleteBook,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onOpenFilePicker: () -> Unit,
    onOpenFromLibrary: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    var shelfFilter by remember { mutableIntStateOf(0) } // 0=All,1=Reading,2=Finished,3=Unread
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredBooks = remember(uiState.books, shelfFilter, searchQuery) {
        val byShelf = when (shelfFilter) {
            1 -> uiState.books.filter { it.lastChapterIndex > 0 && !it.isFinished }
            2 -> uiState.books.filter { it.isFinished }
            3 -> uiState.books.filter { it.lastChapterIndex == 0 && !it.isFinished }
            else -> uiState.books
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            byShelf
        } else {
            byShelf.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true)
            }
        }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().background(palette.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.accent)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxSize().background(palette.bg),
        contentPadding = PaddingValues(sp.xxl),
        verticalArrangement = Arrangement.spacedBy(sp.lg),
        horizontalArrangement = Arrangement.spacedBy(sp.lg),
    ) {
        // Header — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(
                weeklyMinutes = uiState.weeklyMinutes,
                savedWords = uiState.savedWordsThisWeek,
                onOpenFilePicker = onOpenFilePicker,
                searchActive = searchActive,
                onToggleSearch = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                },
            )
        }

        // Search bar — full width, only when search is active
        if (searchActive) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibrarySearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            }
        }

        // Streak ribbon — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            StreakRibbon(
                streakDays = uiState.streakDays,
                weeklyMinutes = uiState.weeklyMinutes,
                weeklyGoal = uiState.weeklyGoal,
            )
        }

        // Continue reading hero — full width, only when there are books
        if (uiState.lastBook != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingHero(
                    book = uiState.lastBook!!,
                    onContinue = { onOpenFromLibrary(uiState.lastBook!!.uri) },
                )
            }
        }

        // Shelf header — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShelfHeader(
                totalCount = uiState.books.size,
                readingCount = uiState.books.count { it.lastChapterIndex > 0 && !it.isFinished },
                finishedCount = uiState.books.count { it.isFinished },
                unreadCount = uiState.books.count { it.lastChapterIndex == 0 && !it.isFinished },
                selectedFilter = shelfFilter,
                onFilterSelected = { shelfFilter = it },
            )
        }

        if (uiState.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FadeInOnAppear { EmptyLibrary(onOpenFilePicker = onOpenFilePicker) }
            }
        } else if (filteredBooks.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FadeInOnAppear { NoBooksMatch(query = searchQuery) }
            }
        } else {
            items(filteredBooks, key = { it.uri }) { book ->
                Box(Modifier.animateItem()) {
                    BookCoverCard(
                        book = book,
                        onClick = { onOpenFromLibrary(book.uri) },
                        onDelete = { onDeleteBook(book.uri) },
                    )
                }
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
                text = greeting,
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
                Icon(
                    imageVector = if (searchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = if (searchActive) "Close search" else "Search books",
                    tint = if (searchActive) palette.accent else palette.ink2,
                )
            }
            LibraryTagButton(text = "Open book", onClick = onOpenFilePicker)
        }
    }
}

// ── Library search ───────────────────────────────────────────────────────

@Composable
private fun LibrarySearchBar(
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
private fun NoBooksMatch(query: String) {
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
private fun StreakRibbon(streakDays: Int, weeklyMinutes: Int, weeklyGoal: Int) {
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

@Composable
private fun ContinueReadingHero(book: BookItem, onContinue: () -> Unit) {
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

        // Title block
        Column(modifier = Modifier.weight(1f)) {
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
            modifier = Modifier.width(160.dp),
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
                    text = "Today",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = palette.ink,
                )
            }
            // Continue button
            BookplateButton(
                text = "Continue reading",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
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

// ── Book grid card ────────────────────────────────────────────────────────

@Composable
private fun BookCoverCard(book: BookItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalReaderPalette.current
    val spec = coverSpec(book.title, book.uri)
    val progress = if (book.chapterCount > 0) book.lastChapterIndex.toFloat() / book.chapterCount else 0f
    val finished = book.isFinished
    var showConfirmDelete by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = {
                Text(
                    text = "Delete book",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = palette.ink,
                )
            },
            text = {
                Text(
                    text = "\"${book.title}\" will be removed from your library.",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = palette.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    onDelete()
                }) {
                    Text(
                        text = "Delete",
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text(
                        text = "Cancel",
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = palette.ink2,
                    )
                }
            },
            containerColor = palette.bg2,
            tonalElevation = 0.dp,
        )
    }

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
                coverFilePath = book.coverPath,
                modifier = Modifier.fillMaxWidth(),
            )
            // Progress overlay at bottom
            if (progress > 0f && !finished) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(palette.bg2.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(palette.accent)
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
                        .background(palette.moss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = palette.bg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // 3-dot context menu at bottom-right
            // 48dp touch target around the 24dp visual delete dot
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(48.dp)
                    .clickable { showConfirmDelete = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete book",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.title,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = palette.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "by ${book.author}",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
            color = palette.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (progress > 0f) {
            Text(
                text = "${(progress * 100).toInt()}% · CH ${book.lastChapterIndex + 1}/${book.chapterCount}",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp,
                color = palette.ink3,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────

@Composable
private fun EmptyLibrary(onOpenFilePicker: () -> Unit) {
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
    coverFilePath: String? = null,
    modifier: Modifier = Modifier,
) {
    val coverBitmap by produceState<ImageBitmap?>(null, coverFilePath) {
        value = if (coverFilePath != null) {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(coverFilePath)?.asImageBitmap() }.getOrNull()
            }
        } else null
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(bgColor),
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap!!,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
    val palette = LocalReaderPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(palette.bg3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(palette.accent)
        )
    }
}
