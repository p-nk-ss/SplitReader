package com.example.splitreader.presentation.reader

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ReaderRoute(
    filePath: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(filePath) {
        viewModel.loadBook(Uri.parse(filePath))
    }

    ReaderScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSelectChapter = viewModel::selectChapter,
        onPreviousChapter = viewModel::previousChapter,
        onPreviousChapterFromEnd = viewModel::previousChapterFromEnd,
        onNextChapter = viewModel::nextChapter,
        onSetTargetLanguage = viewModel::setTargetLanguage,
        onUpdateScrollPosition = { index, offset -> viewModel.updateScrollPosition(index, offset) },
        onConsumeScrollRestore = viewModel::consumeScrollRestore,
        onAdjustTextSize = viewModel::adjustTextSize,
        onSetNavigationSide = viewModel::setNavigationSide,
        onSetReaderTheme = viewModel::setReaderTheme,
        onAdjustLineHeight = viewModel::adjustLineHeight,
        onSetSplitRatio = viewModel::setSplitRatio,
        onToggleTranslation = viewModel::toggleTranslation,
        onSetHorizontalMargin = viewModel::setHorizontalMargin,
    )
}

@Composable
internal fun ReaderScreen(
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onPreviousChapterFromEnd: () -> Unit,
    onNextChapter: () -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onUpdateScrollPosition: (Int, Int) -> Unit,
    onConsumeScrollRestore: () -> Unit,
    onAdjustTextSize: (Float) -> Unit,
    onSetNavigationSide: (NavigationSide) -> Unit,
    onSetReaderTheme: (ReaderTheme) -> Unit,
    onAdjustLineHeight: (Float) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onToggleTranslation: () -> Unit,
    onSetHorizontalMargin: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is ReaderUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is ReaderUiState.Error -> ErrorContent(
            message = uiState.message,
            onNavigateBack = onNavigateBack,
            modifier = modifier,
        )

        is ReaderUiState.Success -> ReaderContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onSelectChapter = onSelectChapter,
            onPreviousChapter = onPreviousChapter,
            onPreviousChapterFromEnd = onPreviousChapterFromEnd,
            onNextChapter = onNextChapter,
            onSetTargetLanguage = onSetTargetLanguage,
            onUpdateScrollPosition = onUpdateScrollPosition,
            onConsumeScrollRestore = onConsumeScrollRestore,
            onAdjustTextSize = onAdjustTextSize,
            onSetNavigationSide = onSetNavigationSide,
            onSetReaderTheme = onSetReaderTheme,
            onAdjustLineHeight = onAdjustLineHeight,
            onSetSplitRatio = onSetSplitRatio,
            onToggleTranslation = onToggleTranslation,
            onSetHorizontalMargin = onSetHorizontalMargin,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    uiState: ReaderUiState.Success,
    onNavigateBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onPreviousChapterFromEnd: () -> Unit,
    onNextChapter: () -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onUpdateScrollPosition: (Int, Int) -> Unit,
    onConsumeScrollRestore: () -> Unit,
    onAdjustTextSize: (Float) -> Unit,
    onSetNavigationSide: (NavigationSide) -> Unit,
    onSetReaderTheme: (ReaderTheme) -> Unit,
    onAdjustLineHeight: (Float) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onToggleTranslation: () -> Unit,
    onSetHorizontalMargin: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showChapterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }

    val pageStarts = remember(uiState.currentChapterIndex) { mutableStateListOf(0) }
    var currentPage by remember(uiState.currentChapterIndex) { mutableIntStateOf(0) }
    // Capture at composition time so LaunchedEffect bodies don't read a stale live value
    val pendingScrollSnapshot = uiState.pendingScrollPosition
    // Prevents double-press: ViewModel updates synchronously but UI state lags by one frame
    var chapterNavLock by remember { mutableStateOf(false) }
    // Index of paragraph highlighted by long press (-1 = none)
    var highlightedParagraphIndex by remember { mutableIntStateOf(-1) }

    // Reading progress for status bar
    val totalBookParagraphs = remember(uiState.book) {
        uiState.book.chapters.sumOf { it.paragraphs.size }
    }
    val paragraphsBeforeChapter = remember(uiState.book, uiState.currentChapterIndex) {
        uiState.book.chapters.take(uiState.currentChapterIndex).sumOf { it.paragraphs.size }
    }
    val bookPercent by remember(totalBookParagraphs, paragraphsBeforeChapter) {
        derivedStateOf {
            if (totalBookParagraphs > 0) {
                ((paragraphsBeforeChapter + listState.firstVisibleItemIndex) * 100 /
                    totalBookParagraphs).coerceIn(0, 100)
            } else 0
        }
    }

    // Auto-hide top bar after 3 seconds of inactivity
    LaunchedEffect(showTopBar) {
        if (showTopBar) {
            delay(3_000)
            showTopBar = false
        }
    }

    // Restores a specific scroll position (initial book load or going to chapter end)
    LaunchedEffect(uiState.pendingScrollPosition) {
        if (pendingScrollSnapshot >= 0) {
            pageStarts.clear()
            pageStarts.add(pendingScrollSnapshot)
            currentPage = 0
            listState.scrollToItem(pendingScrollSnapshot, uiState.pendingScrollOffset)
            onConsumeScrollRestore()
        }
    }

    // Scrolls to the start when switching chapters with no explicit target position
    LaunchedEffect(uiState.currentChapterIndex) {
        chapterNavLock = false
        highlightedParagraphIndex = -1
        if (pendingScrollSnapshot < 0) {
            listState.scrollToItem(0)
        }
    }

    // Auto-saves scroll position via snapshotFlow — no manual calls needed in nav handlers
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(300)
            .collect { (index, offset) -> onUpdateScrollPosition(index, offset) }
    }

    LaunchedEffect(uiState.translationState) {
        if (uiState.translationState is TranslationState.Error) {
            snackbarHostState.showSnackbar(
                "Translation error: ${(uiState.translationState as TranslationState.Error).message}"
            )
        }
    }

    val navigateNextPage: () -> Unit = navigate@{
        val layoutInfo = listState.layoutInfo
        val viewportEnd = layoutInfo.viewportEndOffset
        val total = uiState.currentChapter.paragraphs.size

        val nextStart = layoutInfo.visibleItemsInfo
            .firstOrNull { it.offset + it.size > viewportEnd }
            ?.index
            ?: layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index + 1 }
            ?: return@navigate

        if (nextStart >= total) {
            if (uiState.canGoNext && !chapterNavLock) {
                chapterNavLock = true
                onNextChapter()
            }
            return@navigate
        }

        while (pageStarts.size > currentPage + 1) pageStarts.removeLast()
        pageStarts.add(nextStart)
        currentPage++

        coroutineScope.launch { listState.scrollToItem(nextStart) }
    }

    val navigatePrevPage: () -> Unit = navigate@{
        if (currentPage > 0) {
            currentPage--
            coroutineScope.launch { listState.scrollToItem(pageStarts[currentPage]) }
            return@navigate
        }

        // currentPage == 0: check if there's unvisited content before current position
        val firstVisible = listState.firstVisibleItemIndex
        if (firstVisible > 0) {
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val prevStart = (firstVisible - visibleCount).coerceAtLeast(0)
            pageStarts.add(0, prevStart)
            coroutineScope.launch { listState.scrollToItem(prevStart) }
            return@navigate
        }

        if (uiState.canGoPrevious && !chapterNavLock) {
            chapterNavLock = true
            onPreviousChapterFromEnd()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        // Single Box at top level so AnimatedVisibility overlays are NOT inside ColumnScope,
        // avoiding the "cannot be called with implicit receiver" compile error.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Main layout: reading area + status bar
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (uiState.navigationSide == NavigationSide.LEFT) {
                        SideNavPanel(
                            onPreviousPage = navigatePrevPage,
                            onNextPage = navigateNextPage,
                        )
                    }

                    // Paired text — tap anywhere here to show/hide the top bar
                    PairedTextPane(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        originalParagraphs = uiState.currentChapter.paragraphs,
                        translatedParagraphs = uiState.translatedParagraphs,
                        sourceLanguage = uiState.sourceLanguage,
                        targetLanguage = uiState.targetLanguage,
                        listState = listState,
                        textSize = uiState.textSize,
                        lineHeightMultiplier = uiState.lineHeightMultiplier,
                        splitRatio = uiState.splitRatio,
                        showTranslation = uiState.showTranslation,
                        readerTheme = uiState.readerTheme,
                        epigraphCount = uiState.currentChapter.epigraphCount,
                        horizontalMargin = uiState.horizontalMargin,
                        highlightedParagraphIndex = highlightedParagraphIndex,
                        onTap = { showTopBar = !showTopBar },
                        onParagraphLongPress = { index -> highlightedParagraphIndex = index },
                    )

                    if (uiState.navigationSide == NavigationSide.RIGHT) {
                        SideNavPanel(
                            onPreviousPage = navigatePrevPage,
                            onNextPage = navigateNextPage,
                        )
                    }
                }

                ReadingStatusBar(
                    chapterCurrent = uiState.currentChapterIndex + 1,
                    chapterTotal = uiState.book.chapters.size,
                    chapterTitle = uiState.currentChapter.title,
                    bookPercent = bookPercent,
                )
            }

            // Overlays — directly inside Box (not Column), so no ColumnScope conflict

            // Translation progress slides up from the bottom
            AnimatedVisibility(
                visible = uiState.translationState is TranslationState.DownloadingModel ||
                    uiState.translationState is TranslationState.Translating,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                TranslationProgressBanner(uiState.translationState)
            }

            // Top app bar overlay — slides in from top on tap, auto-hides after 3 s
            AnimatedVisibility(
                visible = showTopBar,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = {
                        Text(
                            text = uiState.book.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showLanguagePicker = true; showTopBar = true }) {
                            Icon(Icons.Default.Translate, contentDescription = "Language")
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true; showTopBar = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Display settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { showDisplaySettings = true; showOverflowMenu = false; showTopBar = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Select chapter") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                    onClick = { showChapterMenu = true; showOverflowMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Go to beginning") },
                                    leadingIcon = { Icon(Icons.Default.SkipPrevious, null) },
                                    onClick = {
                                        onSelectChapter(0)
                                        showOverflowMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Previous chapter") },
                                    leadingIcon = { Icon(Icons.Default.SkipPrevious, null) },
                                    enabled = uiState.canGoPrevious,
                                    onClick = { onPreviousChapter(); showOverflowMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Next chapter") },
                                    leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                                    enabled = uiState.canGoNext,
                                    onClick = { onNextChapter(); showOverflowMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.navigationSide == NavigationSide.RIGHT)
                                                "Move buttons to left"
                                            else
                                                "Move buttons to right",
                                        )
                                    },
                                    onClick = {
                                        onSetNavigationSide(
                                            if (uiState.navigationSide == NavigationSide.RIGHT)
                                                NavigationSide.LEFT
                                            else
                                                NavigationSide.RIGHT,
                                        )
                                        showOverflowMenu = false
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showDisplaySettings) {
        DisplaySettingsSheet(
            textSize = uiState.textSize,
            lineHeightMultiplier = uiState.lineHeightMultiplier,
            splitRatio = uiState.splitRatio,
            showTranslation = uiState.showTranslation,
            readerTheme = uiState.readerTheme,
            horizontalMargin = uiState.horizontalMargin,
            onAdjustTextSize = onAdjustTextSize,
            onAdjustLineHeight = onAdjustLineHeight,
            onSetSplitRatio = onSetSplitRatio,
            onToggleTranslation = onToggleTranslation,
            onSetReaderTheme = onSetReaderTheme,
            onSetHorizontalMargin = onSetHorizontalMargin,
            onDismiss = { showDisplaySettings = false },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            currentLanguage = uiState.targetLanguage,
            onLanguageSelected = { lang ->
                onSetTargetLanguage(lang)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showChapterMenu) {
        ChapterPickerDialog(
            chapters = uiState.book.chapters,
            currentIndex = uiState.currentChapterIndex,
            onChapterSelected = { index ->
                onSelectChapter(index)
                showChapterMenu = false
            },
            onDismiss = { showChapterMenu = false },
        )
    }
}

// Two-half navigation panel: upper half = previous page, lower half = next page
@Composable
private fun SideNavPanel(
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .background(panelColor),
    ) {
        // Upper half — previous page
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onPreviousPage),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous page",
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )

        // Lower half — next page
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onNextPage),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next page",
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ReadingStatusBar(
    chapterCurrent: Int,
    chapterTotal: Int,
    chapterTitle: String,
    bookPercent: Int,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.labelSmall
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Ch. $chapterCurrent / $chapterTotal",
            style = textStyle,
            color = textColor,
            maxLines = 1,
        )
        Text(
            text = chapterTitle,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = "$bookPercent%",
            style = textStyle,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun PairedTextPane(
    originalParagraphs: List<String>,
    translatedParagraphs: List<String>,
    sourceLanguage: Language,
    targetLanguage: Language,
    listState: LazyListState,
    textSize: Float,
    lineHeightMultiplier: Float,
    splitRatio: Float,
    showTranslation: Boolean,
    readerTheme: ReaderTheme,
    epigraphCount: Int,
    horizontalMargin: Float,
    highlightedParagraphIndex: Int,
    onTap: () -> Unit,
    onParagraphLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (readerTheme) {
        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.surface
        ReaderTheme.SEPIA   -> Color(0xFFF5E6C4)
        ReaderTheme.NIGHT   -> Color(0xFF1A1A2E)
        ReaderTheme.AMOLED  -> Color.Black
    }
    val surfaceColor = when (readerTheme) {
        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
        ReaderTheme.SEPIA   -> Color(0xFFEDD9A3)
        ReaderTheme.NIGHT   -> Color(0xFF16213E)
        ReaderTheme.AMOLED  -> Color(0xFF0D0D0D)
    }
    val textColor = when (readerTheme) {
        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.onSurface
        ReaderTheme.SEPIA   -> Color(0xFF3C2415)
        ReaderTheme.NIGHT   -> Color(0xFFCCCCDD)
        ReaderTheme.AMOLED  -> Color(0xFFCCCCCC)
    }
    val secondaryTextColor = when (readerTheme) {
        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
        ReaderTheme.SEPIA   -> Color(0xFF6B4423)
        ReaderTheme.NIGHT   -> Color(0xFF9999AA)
        ReaderTheme.AMOLED  -> Color(0xFF999999)
    }
    val dividerColor = when (readerTheme) {
        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.outlineVariant
        ReaderTheme.SEPIA   -> Color(0xFFCFB98A)
        ReaderTheme.NIGHT   -> Color(0xFF2A2A4A)
        ReaderTheme.AMOLED  -> Color(0xFF222222)
    }
    val highlightColor = when (readerTheme) {
        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ReaderTheme.SEPIA   -> Color(0xFFC8A96E).copy(alpha = 0.35f)
        ReaderTheme.NIGHT   -> Color(0xFF3A3A6A).copy(alpha = 0.7f)
        ReaderTheme.AMOLED  -> Color(0xFF1A1A3A).copy(alpha = 0.9f)
    }

    Column(
        modifier = modifier
            .background(bgColor)
            .drawBehind {
                if (showTranslation) {
                    val x = size.width * splitRatio
                    drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor),
        ) {
            Row(
                modifier = Modifier
                    .weight(if (showTranslation) splitRatio else 1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${sourceLanguage.flag} ${sourceLanguage.displayName}", style = MaterialTheme.typography.labelMedium, color = textColor)
                Text("Original", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
            }
            if (showTranslation) {
                Row(
                    modifier = Modifier
                        .weight(1f - splitRatio)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${targetLanguage.flag} ${targetLanguage.displayName}", style = MaterialTheme.typography.labelMedium, color = textColor)
                    Text("Translation", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                }
            }
        }
        HorizontalDivider(color = dividerColor)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                // Tap on empty space between paragraphs toggles the top bar
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                },
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(originalParagraphs) { index, original ->
                val translated = translatedParagraphs.getOrElse(index) { "" }
                val isEpigraph = index < epigraphCount
                val paraFontSize = if (isEpigraph) (textSize * 0.9f).sp else textSize.sp
                val paraLineHeight = (textSize * (if (isEpigraph) lineHeightMultiplier * 0.9f else lineHeightMultiplier)).sp
                val paraColor = if (isEpigraph) secondaryTextColor else textColor
                val paraFontStyle = if (isEpigraph)
                    androidx.compose.ui.text.font.FontStyle.Italic
                else
                    androidx.compose.ui.text.font.FontStyle.Normal
                val isHighlighted = index == highlightedParagraphIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHighlighted) highlightColor else Color.Transparent)
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = { onTap() },
                                onLongPress = { onParagraphLongPress(index) },
                            )
                        },
                ) {
                    Text(
                        text = original,
                        modifier = Modifier
                            .weight(if (showTranslation) splitRatio else 1f)
                            .padding(horizontal = horizontalMargin.dp),
                        fontSize = paraFontSize,
                        lineHeight = paraLineHeight,
                        color = paraColor,
                        fontStyle = paraFontStyle,
                    )
                    if (showTranslation) {
                        Text(
                            text = translated,
                            modifier = Modifier
                                .weight(1f - splitRatio)
                                .padding(horizontal = horizontalMargin.dp),
                            fontSize = paraFontSize,
                            lineHeight = paraLineHeight,
                            color = paraColor,
                            fontStyle = paraFontStyle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationProgressBanner(translationState: TranslationState) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = when (translationState) {
                    is TranslationState.DownloadingModel -> "Downloading translation model…"
                    is TranslationState.Translating -> "Translating… ${translationState.progress}%"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Translation language",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(Language.entries) { language ->
                val isSelected = language == currentLanguage
                Card(
                    onClick = { onLanguageSelected(language) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(language.flag, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ChapterPickerDialog(
    chapters: List<Chapter>,
    currentIndex: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Chapter") },
        text = {
            LazyColumn {
                itemsIndexed(chapters) { index, chapter ->
                    ListItem(
                        headlineContent = {
                            Text(chapter.title.ifEmpty { "Chapter ${index + 1}" })
                        },
                        leadingContent = {
                            RadioButton(
                                selected = index == currentIndex,
                                onClick = { onChapterSelected(index) },
                            )
                        },
                        modifier = Modifier.clickable { onChapterSelected(index) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySettingsSheet(
    textSize: Float,
    lineHeightMultiplier: Float,
    splitRatio: Float,
    showTranslation: Boolean,
    readerTheme: ReaderTheme,
    horizontalMargin: Float,
    onAdjustTextSize: (Float) -> Unit,
    onAdjustLineHeight: (Float) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onToggleTranslation: () -> Unit,
    onSetReaderTheme: (ReaderTheme) -> Unit,
    onSetHorizontalMargin: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Display settings", style = MaterialTheme.typography.titleMedium)

            // Font size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font size", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onAdjustTextSize(-2f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Decrease")
                }
                Text(
                    "${textSize.toInt()}sp",
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { onAdjustTextSize(2f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Increase")
                }
            }

            // Line height
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Line height", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onAdjustLineHeight(-0.1f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Decrease")
                }
                Text(
                    "×${"%.1f".format(lineHeightMultiplier)}",
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { onAdjustLineHeight(0.1f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Increase")
                }
            }

            // Horizontal margin
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Side margins", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onSetHorizontalMargin(horizontalMargin - 2f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Decrease")
                }
                Text(
                    "${horizontalMargin.toInt()}dp",
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { onSetHorizontalMargin(horizontalMargin + 2f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Increase")
                }
            }

            // Show translation toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Show translation", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showTranslation, onCheckedChange = { onToggleTranslation() })
            }

            // Split ratio (only when translation visible)
            if (showTranslation) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Original width", style = MaterialTheme.typography.bodyMedium)
                        Text("${(splitRatio * 100).toInt()}% / ${((1f - splitRatio) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = splitRatio,
                        onValueChange = onSetSplitRatio,
                        valueRange = 0.3f..0.7f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider()

            // Theme picker
            Text("Reading theme", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderTheme.entries.forEach { theme ->
                    val isSelected = theme == readerTheme
                    val (bg, label) = when (theme) {
                        ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.surface to "Default"
                        ReaderTheme.SEPIA   -> Color(0xFFF5E6C4) to "Sepia"
                        ReaderTheme.NIGHT   -> Color(0xFF1A1A2E) to "Night"
                        ReaderTheme.AMOLED  -> Color.Black to "AMOLED"
                    }
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                    Surface(
                        onClick = { onSetReaderTheme(theme) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = bg,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = borderColor,
                        ),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = when (theme) {
                                ReaderTheme.DEFAULT -> MaterialTheme.colorScheme.onSurface
                                ReaderTheme.SEPIA   -> Color(0xFF3C2415)
                                ReaderTheme.NIGHT   -> Color(0xFFCCCCDD)
                                ReaderTheme.AMOLED  -> Color(0xFFCCCCCC)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNavigateBack) { Text("Go Back") }
    }
}
