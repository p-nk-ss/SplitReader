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

// ── Entry point ─────────────────────────────────────────────────────────

@Composable
internal fun HomeRoute(
    onNavigateToReader: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val premiumViewModel: PremiumViewModel = hiltViewModel()
    val price by premiumViewModel.priceText.collectAsStateWithLifecycle()
    PurchaseEventEffect(premiumViewModel)
    var showLimitDialog by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openBook(it) } }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { path -> onNavigateToReader(path) }
    }
    LaunchedEffect(Unit) {
        viewModel.limitReachedEvent.collect { showLimitDialog = true }
    }

    if (showLimitDialog) {
        LibraryLimitDialog(
            onDismiss = { showLimitDialog = false },
            onUpgrade = {
                showLimitDialog = false
                premiumViewModel.upgrade(activity)
            },
            priceText = price,
        )
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
        // Skeleton stand-in that mirrors the real grid metrics, so the layout doesn't
        // jump when books arrive (vs. a centered spinner over a blank screen).
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize().background(palette.bg),
            contentPadding = PaddingValues(sp.xxl),
            verticalArrangement = Arrangement.spacedBy(sp.lg),
            horizontalArrangement = Arrangement.spacedBy(sp.lg),
            userScrollEnabled = false,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { HomeHeaderSkeleton() }
            items(14) { SkeletonCoverCard() }
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
                userName = uiState.userName,
                onOpenFilePicker = onOpenFilePicker,
                searchActive = searchActive,
                onToggleSearch = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                },
            )
        }

        // Search bar — full width. Kept in the grid always so it can animate both in
        // and out; AnimatedVisibility collapses it to zero height when inactive.
        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedVisibility(
                visible = searchActive,
                enter = expandVertically(tween(MotionTokens.Medium, easing = MotionTokens.EaseStandard)) +
                    fadeIn(tween(MotionTokens.Medium)),
                exit = shrinkVertically(tween(MotionTokens.Fast, easing = MotionTokens.EaseStandard)) +
                    fadeOut(tween(MotionTokens.Fast)),
            ) {
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
        val lastBook = uiState.lastBook
        if (lastBook != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingHero(
                    book = lastBook,
                    minutesToday = uiState.minutesToday,
                    onContinue = { onOpenFromLibrary(lastBook.uri) },
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
            itemsIndexed(filteredBooks, key = { _, book -> book.uri }) { index, book ->
                StaggeredAppear(index = index, modifier = Modifier.animateItem()) {
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

