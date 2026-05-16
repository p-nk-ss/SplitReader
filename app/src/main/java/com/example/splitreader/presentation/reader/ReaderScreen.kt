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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import kotlinx.coroutines.delay
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
        onNextChapter = viewModel::nextChapter,
        onSetTargetLanguage = viewModel::setTargetLanguage,
        onUpdateScrollPosition = viewModel::updateScrollPosition,
        onConsumeScrollRestore = viewModel::consumeScrollRestore,
        onAdjustTextSize = viewModel::adjustTextSize,
        onSetNavigationSide = viewModel::setNavigationSide,
    )
}

@Composable
internal fun ReaderScreen(
    uiState: ReaderUiState,
    onNavigateBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onUpdateScrollPosition: (Int) -> Unit,
    onConsumeScrollRestore: () -> Unit,
    onAdjustTextSize: (Float) -> Unit,
    onSetNavigationSide: (NavigationSide) -> Unit,
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
            onNextChapter = onNextChapter,
            onSetTargetLanguage = onSetTargetLanguage,
            onUpdateScrollPosition = onUpdateScrollPosition,
            onConsumeScrollRestore = onConsumeScrollRestore,
            onAdjustTextSize = onAdjustTextSize,
            onSetNavigationSide = onSetNavigationSide,
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
    onNextChapter: () -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onUpdateScrollPosition: (Int) -> Unit,
    onConsumeScrollRestore: () -> Unit,
    onAdjustTextSize: (Float) -> Unit,
    onSetNavigationSide: (NavigationSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showChapterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }

    val pageStarts = remember(uiState.currentChapterIndex) { mutableStateListOf(0) }
    var currentPage by remember(uiState.currentChapterIndex) { mutableIntStateOf(0) }

    // Reading progress for status bar
    val totalBookParagraphs = remember(uiState.book) {
        uiState.book.chapters.sumOf { it.paragraphs.size }
    }
    val paragraphsBeforeChapter = remember(uiState.book, uiState.currentChapterIndex) {
        uiState.book.chapters.take(uiState.currentChapterIndex).sumOf { it.paragraphs.size }
    }
    val bookPercent = if (totalBookParagraphs > 0) {
        ((paragraphsBeforeChapter + listState.firstVisibleItemIndex) * 100 /
            totalBookParagraphs).coerceIn(0, 100)
    } else 0

    // Auto-hide top bar after 3 seconds of inactivity
    LaunchedEffect(showTopBar) {
        if (showTopBar) {
            delay(3_000)
            showTopBar = false
        }
    }

    LaunchedEffect(uiState.pendingScrollPosition) {
        if (uiState.pendingScrollPosition >= 0) {
            pageStarts.clear()
            pageStarts.add(uiState.pendingScrollPosition)
            currentPage = 0
            listState.scrollToItem(uiState.pendingScrollPosition)
            onConsumeScrollRestore()
        }
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
            if (uiState.canGoNext) onNextChapter()
            return@navigate
        }

        while (pageStarts.size > currentPage + 1) pageStarts.removeLast()
        pageStarts.add(nextStart)
        currentPage++

        coroutineScope.launch { listState.scrollToItem(nextStart) }
        onUpdateScrollPosition(nextStart)
    }

    val navigatePrevPage: () -> Unit = {
        if (currentPage > 0) {
            currentPage--
            val prevStart = pageStarts[currentPage]
            coroutineScope.launch { listState.scrollToItem(prevStart) }
            onUpdateScrollPosition(prevStart)
        } else if (uiState.canGoPrevious) {
            onPreviousChapter()
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
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { showTopBar = !showTopBar })
                            },
                        originalParagraphs = uiState.currentChapter.paragraphs,
                        translatedParagraphs = uiState.translatedParagraphs,
                        sourceLanguage = uiState.sourceLanguage,
                        targetLanguage = uiState.targetLanguage,
                        listState = listState,
                        textSize = uiState.textSize,
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
                                    text = { Text("Increase text size") },
                                    leadingIcon = { Icon(Icons.Default.ZoomIn, null) },
                                    onClick = { onAdjustTextSize(2f); showOverflowMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Decrease text size") },
                                    leadingIcon = { Icon(Icons.Default.ZoomOut, null) },
                                    onClick = { onAdjustTextSize(-2f); showOverflowMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Select chapter") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                    onClick = { showChapterMenu = true; showOverflowMenu = false },
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
    bookPercent: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val textStyle = MaterialTheme.typography.labelSmall
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant

        Text(
            text = "Ch. $chapterCurrent / $chapterTotal",
            style = textStyle,
            color = textColor,
        )
        Text(
            text = "$bookPercent%",
            style = textStyle,
            color = textColor,
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
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier.drawBehind {
            val x = size.width / 2f
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${sourceLanguage.flag} ${sourceLanguage.displayName}", style = MaterialTheme.typography.labelMedium)
                Text("Original", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${targetLanguage.flag} ${targetLanguage.displayName}", style = MaterialTheme.typography.labelMedium)
                Text("Translation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(originalParagraphs) { index, original ->
                val translated = translatedParagraphs.getOrElse(index) { "" }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = original,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.5f).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = translated,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        fontSize = textSize.sp,
                        lineHeight = (textSize * 1.5f).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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
