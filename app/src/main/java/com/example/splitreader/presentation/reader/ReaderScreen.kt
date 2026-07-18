package com.example.splitreader.presentation.reader

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.R
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.SaveWordResult
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.ReadingFont
import com.example.splitreader.presentation.theme.readerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ── Entry point ───────────────────────────────────────────────────────────

// TODO(architecture): this file is very large (~1.7k lines) and mixes the reader scaffold, the
//  book-spread renderer, the translation bubble, paragraph rendering, and several dialogs/overlays.
//  Consider splitting into focused files (e.g. ReaderTopBar, BookSpread, TranslationBubble,
//  ReaderDialogs) to improve navigability. No behavior change required.
@Composable
internal fun ReaderRoute(
    filePath: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(filePath) {
        viewModel.loadBook(Uri.parse(filePath))
    }

    // Track reading time: resume on foreground, persist the session on pause/navigate-away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resumeSession()
                Lifecycle.Event.ON_PAUSE -> viewModel.pauseSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pauseSession()
        }
    }

    // Toast feedback after a save attempt from the translation bubble
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.wordSaveEvent.collect { result ->
            val message = when (result) {
                SaveWordResult.SAVED -> context.getString(R.string.saved_to_dictionary)
                SaveWordResult.DUPLICATE -> context.getString(R.string.already_in_dictionary)
                SaveWordResult.EMPTY -> context.getString(R.string.nothing_to_save)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = uiState) {
        is ReaderUiState.Loading -> ReaderLoadingScreen()
        is ReaderUiState.Error   -> ReaderErrorScreen(message = s.message, onBack = onNavigateBack)
        is ReaderUiState.Success -> ReaderContent(
            state = s,
            onNavigateBack = onNavigateBack,
            onSelectChapter = viewModel::selectChapter,
            onSetTargetLanguage = viewModel::setTargetLanguage,
            onSetReaderTheme = viewModel::setReaderTheme,
            onAdjustTextSize = viewModel::adjustTextSize,
            onAdjustLineHeight = viewModel::adjustLineHeight,
            onSetReadingFont = viewModel::setReadingFont,
            onSetLetterSpacing = viewModel::setLetterSpacing,
            onSetTextIndent = viewModel::setTextIndent,
            onSetParagraphSpacing = viewModel::setParagraphSpacing,
            onSetJustifyText = viewModel::setJustifyText,
            onSetSplitRatio = viewModel::setSplitRatio,
            onToggleTranslation = viewModel::toggleTranslation,
            onToggleIllustrations = viewModel::toggleIllustrations,
            onSetNavigationSide = viewModel::setNavigationSide,
            onSetHorizontalMargin = viewModel::setHorizontalMargin,
            onUpdateScrollPosition = viewModel::updateScrollPosition,
            onMarkFinished = viewModel::markFinished,
            onToggleBookmark = viewModel::toggleBookmarkAtCurrentPosition,
            onRemoveBookmark = viewModel::removeBookmarkAt,
            onJumpToBookmark = viewModel::jumpToBookmark,
            onConsumeScrollRestore = viewModel::consumeScrollRestore,
            onVisibleRange = viewModel::onVisibleRange,
            onSaveWord = viewModel::saveWord,
            onSpeak = viewModel::speak,
            onSelectWord = viewModel::selectWord,
            onClearWordSelection = viewModel::clearWordSelection,
            onSelectionDragged = viewModel::updateWordSelectionRange,
            onSelectProvider = viewModel::selectProvider,
            onConfigureProvider = viewModel::configureProvider,
            onClearProvider = viewModel::clearProvider,
            onRefreshTranslationUsage = viewModel::refreshTranslationUsage,
            onResetTranslationUsage = viewModel::resetTranslationUsage,
            onRetryTranslation = viewModel::retryTranslation,
            onTranslateWholeChapter = viewModel::translateWholeChapter,
        )
    }
}

@Composable
private fun ReaderLoadingScreen() {
    val palette = LocalReaderPalette.current
    Box(Modifier.fillMaxSize().background(palette.bg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = palette.accent)
    }
}

@Composable
private fun ReaderErrorScreen(message: String, onBack: () -> Unit) {
    val palette = LocalReaderPalette.current
    Box(Modifier.fillMaxSize().background(palette.bg).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontFamily = Newsreader, fontSize = 16.sp, color = palette.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(palette.ink).clickable(onClick = onBack).padding(12.dp, 8.dp)) {
                Text("Go back", fontFamily = Newsreader, fontStyle = FontStyle.Italic, color = palette.bg)
            }
        }
    }
}

// ── Reader content ────────────────────────────────────────────────────────

/** How long the reader's top/bottom bars stay visible before auto-hiding for an immersive read. */
private const val READER_BARS_AUTO_HIDE_MS = 3500L

@Composable
private fun ReaderContent(
    state: ReaderUiState.Success,
    onNavigateBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onSetReaderTheme: (ReaderThemeKey) -> Unit,
    onAdjustTextSize: (Float) -> Unit,
    onAdjustLineHeight: (Float) -> Unit,
    onSetReadingFont: (ReadingFont) -> Unit,
    onSetLetterSpacing: (Float) -> Unit,
    onSetTextIndent: (Float) -> Unit,
    onSetParagraphSpacing: (Float) -> Unit,
    onSetJustifyText: (Boolean) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleIllustrations: () -> Unit,
    onSetNavigationSide: (NavigationSide) -> Unit,
    onSetHorizontalMargin: (Float) -> Unit,
    onUpdateScrollPosition: (Int, Int, Int) -> Unit,
    onMarkFinished: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRemoveBookmark: (Int, Int) -> Unit,
    onJumpToBookmark: (Int, Int) -> Unit,
    onConsumeScrollRestore: () -> Unit,
    onVisibleRange: (Int, Int, Int, Int) -> Unit,
    onSaveWord: (String, Int, Int) -> Unit,
    onSpeak: (String, String) -> Unit,
    onSelectWord: (String, Int, Int, Int, Int) -> Unit,
    onClearWordSelection: () -> Unit,
    onSelectionDragged: (Int, Int) -> Unit,
    onSelectProvider: (TranslationProvider) -> Unit,
    onConfigureProvider: (TranslationProvider, String?, String?) -> Unit,
    onClearProvider: (TranslationProvider) -> Unit,
    onRefreshTranslationUsage: () -> Unit,
    onResetTranslationUsage: (TranslationProvider) -> Unit,
    onRetryTranslation: () -> Unit,
    onTranslateWholeChapter: () -> Unit,
) {
    val palette = readerPalette(state.readerTheme)

    var showLanguagePicker by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var showTranslatorPicker by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var wordHighlightEnabled by remember { mutableStateOf(true) }

    // Immersive reading: the top/bottom bars auto-hide after a delay and reappear on a page tap.
    var barsVisible by remember { mutableStateOf(true) }
    val anyDialogOpen = showLanguagePicker || showDisplaySettings ||
        showChapterPicker || showTranslatorPicker || showBookmarks
    LaunchedEffect(barsVisible, anyDialogOpen) {
        if (barsVisible && !anyDialogOpen) {
            delay(READER_BARS_AUTO_HIDE_MS)
            barsVisible = false
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Pre-compute global item start index for each chapter. Image items are real LazyColumn items
    // (one per illustration) only when illustrations are shown, so the per-chapter item count tracks
    // the toggle — keeping scroll restore / bookmark jumps aligned with what is actually emitted.
    val chapterItemStarts = remember(state.book.chapters, state.showIllustrations) {
        state.book.chapters.runningFold(0) { acc, ch ->
            acc + 1 + ch.paragraphs.size + (if (state.showIllustrations) ch.images.size else 0)
        }.dropLast(1)
    }

    // Restore scroll position on book load
    LaunchedEffect(state.pendingScrollPosition) {
        if (state.pendingScrollPosition >= 0) {
            val globalPos = chapterItemStarts.getOrElse(state.currentChapterIndex) { 0 } + state.pendingScrollPosition
            listState.scrollToItem(globalPos, state.pendingScrollOffset)
            onConsumeScrollRestore()
        }
    }

    // Persist scroll position (decomposed to chapter + local index)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(300)
            .collect { (globalIndex, offset) ->
                val chapter = chapterItemStarts.indexOfLast { it <= globalIndex }.coerceAtLeast(0)
                val localIndex = globalIndex - chapterItemStarts.getOrElse(chapter) { 0 }
                onUpdateScrollPosition(chapter, localIndex, offset)
            }
    }

    // Mark the book finished once the user scrolls to the end of the last chapter.
    // `canScrollBackward` guards against books that fit one screen / were merely opened.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            Triple(
                info.visibleItemsInfo.lastOrNull()?.index ?: -1,
                info.totalItemsCount,
                listState.canScrollBackward,
            )
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total, scrolledDown) ->
                if (total > 0 && scrolledDown && lastVisible >= total - 2) onMarkFinished()
            }
    }

    // Report the *visible paragraph span* (first→last visible item, which may cross chapter
    // boundaries). ChapterTranslationManager translates that window plus a bounded look-ahead/behind,
    // so everything on screen — including several short chapters at once — gets translated, and the
    // look-ahead/behind owns the rest.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            (info.firstOrNull()?.index ?: 0) to (info.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .debounce(120)
            .collect { (firstIndex, lastIndex) ->
                val startChapter = chapterItemStarts.indexOfLast { it <= firstIndex }.coerceAtLeast(0)
                val startLocal = firstIndex - chapterItemStarts.getOrElse(startChapter) { 0 }
                val endChapter = chapterItemStarts.indexOfLast { it <= lastIndex }.coerceAtLeast(0)
                val endLocal = lastIndex - chapterItemStarts.getOrElse(endChapter) { 0 }
                onVisibleRange(startChapter, startLocal, endChapter, endLocal)
            }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPalette provides palette,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg),
        ) {
            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                ReaderTopBar(
                    state = state,
                    onBack = onNavigateBack,
                    onOpenLanguagePicker = { showLanguagePicker = true },
                    onOpenDisplaySettings = { showDisplaySettings = true },
                    onOpenChapterPicker = { showChapterPicker = true },
                    onOpenTranslatorPicker = { showTranslatorPicker = true },
                    onOpenBookmarks = { showBookmarks = true },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                BookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = state.book,
                    chapterTranslations = state.chapterTranslations,
                    showTranslation = state.showTranslation,
                    showIllustrations = state.showIllustrations,
                    splitRatio = state.splitRatio,
                    style = state.readingStyle,
                    wordSelection = state.wordSelection,
                    wordHighlightEnabled = wordHighlightEnabled,
                    listState = listState,
                    onWordSelected = { word, ch, para, start, end -> onSelectWord(word, ch, para, start, end) },
                    onSelectionDragged = onSelectionDragged,
                    onSaveWord = onSaveWord,
                    onSpeak = onSpeak,
                    onDismiss = onClearWordSelection,
                    onToggleBars = { barsVisible = !barsVisible },
                    sourceLang = state.sourceLanguage,
                    targetLang = state.targetLanguage,
                )

                if (state.translationState is TranslationState.DownloadingModel) {
                    TranslationBanner(
                        label = "Preparing translation…",
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                if (state.translationState is TranslationState.Translating) {
                    val progress = (state.translationState as TranslationState.Translating).progress
                    TranslationBanner(
                        label = if (progress <= 0) "Translating…" else "Translating… $progress%",
                        progress = progress,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                if (state.translationState is TranslationState.Error) {
                    TranslationErrorBanner(
                        message = (state.translationState as TranslationState.Error).message,
                        onRetry = onRetryTranslation,
                        onOpenTranslator = { showTranslatorPicker = true },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }

            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ReaderStatusFooter(state = state)
            }
        }

        if (showLanguagePicker) {
            LanguagePickerDialog(
                currentTarget = state.targetLanguage,
                onSelect = { lang ->
                    onSetTargetLanguage(lang)
                    showLanguagePicker = false
                },
                onDismiss = { showLanguagePicker = false },
            )
        }
        if (showDisplaySettings) {
            DisplaySettingsDialog(
                state = state,
                onSetReaderTheme = onSetReaderTheme,
                onAdjustTextSize = onAdjustTextSize,
                onAdjustLineHeight = onAdjustLineHeight,
                onSetReadingFont = onSetReadingFont,
                onSetLetterSpacing = onSetLetterSpacing,
                onSetTextIndent = onSetTextIndent,
                onSetParagraphSpacing = onSetParagraphSpacing,
                onSetJustifyText = onSetJustifyText,
                onSetSplitRatio = onSetSplitRatio,
                onToggleTranslation = onToggleTranslation,
                onToggleIllustrations = onToggleIllustrations,
                wordHighlightEnabled = wordHighlightEnabled,
                onToggleWordHighlight = { wordHighlightEnabled = !wordHighlightEnabled },
                onDismiss = { showDisplaySettings = false },
            )
        }
        if (showTranslatorPicker) {
            LaunchedEffect(Unit) { onRefreshTranslationUsage() }
            TranslatorPickerDialog(
                state = state.translatorConfig,
                onSelect = { provider ->
                    onSelectProvider(provider)
                    showTranslatorPicker = false
                },
                onConfigure = onConfigureProvider,
                onClear = onClearProvider,
                onResetUsage = onResetTranslationUsage,
                // Hide the "translate whole chapter" action when the translation pane is off —
                // translating text the reader can't see would just burn paid quota.
                onTranslateWholeChapter = if (state.showTranslation) onTranslateWholeChapter else null,
                onDismiss = { showTranslatorPicker = false },
            )
        }
        if (showChapterPicker) {
            ChapterPickerDialog(
                book = state.book,
                currentChapterIndex = state.currentChapterIndex,
                onSelect = { idx ->
                    onSelectChapter(idx)
                    coroutineScope.launch { listState.scrollToItem(chapterItemStarts.getOrElse(idx) { 0 }) }
                    showChapterPicker = false
                },
                onDismiss = { showChapterPicker = false },
            )
        }
        if (showBookmarks) {
            BookmarksDialog(
                book = state.book,
                bookmarks = state.bookmarks,
                isCurrentBookmarked = state.isCurrentPositionBookmarked,
                onToggleCurrent = onToggleBookmark,
                onRemove = onRemoveBookmark,
                onJump = { ch, p ->
                    onJumpToBookmark(ch, p)
                    coroutineScope.launch {
                        listState.scrollToItem(chapterItemStarts.getOrElse(ch) { 0 } + p)
                    }
                    showBookmarks = false
                },
                onDismiss = { showBookmarks = false },
            )
        }
    }
}
