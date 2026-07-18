package com.example.splitreader.presentation.reader

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.splitreader.presentation.theme.AnimatedDialog
import com.example.splitreader.presentation.theme.MotionTokens
import com.example.splitreader.presentation.theme.animatedSelection
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.R
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.SaveWordResult
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.ReadingFont
import com.example.splitreader.presentation.theme.readerPalette
import com.example.splitreader.presentation.theme.AmoledPalette
import com.example.splitreader.presentation.theme.NightPalette
import com.example.splitreader.presentation.theme.PaperPalette
import com.example.splitreader.presentation.theme.SepiaPalette
import com.example.splitreader.presentation.ui.SectionEyebrow
import com.example.splitreader.presentation.ui.ToggleRow
import com.example.splitreader.presentation.ui.TypographyControls
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

// ── Top bar ───────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(
    state: ReaderUiState.Success,
    onBack: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenChapterPicker: () -> Unit,
    onOpenTranslatorPicker: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(palette.bg2)
            .drawBehind {
                drawLine(edgeColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }

        // Title chain
        Text(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.ink)) {
                    append(state.book.title)
                }
                withStyle(SpanStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)) {
                    append(" · ")
                }
                withStyle(SpanStyle(fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink2)) {
                    append(state.book.author)
                }
                withStyle(SpanStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)) {
                    append(" · CH ${state.currentChapterIndex + 1}")
                }
            },
        )

        // Language chip
        LangChip(
            source = state.sourceLanguage.badge,
            target = state.targetLanguage.badge,
            onClick = onOpenLanguagePicker,
        )

        IconButton(onClick = onOpenTranslatorPicker) {
            Icon(Icons.Outlined.Translate, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onOpenDisplaySettings) {
            Icon(Icons.Outlined.TextFields, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onOpenChapterPicker) {
            Icon(Icons.Outlined.FormatListBulleted, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onOpenBookmarks) {
            Icon(
                if (state.isCurrentPositionBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmarks",
                tint = if (state.isCurrentPositionBookmarked) palette.accent else palette.ink2,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LangChip(source: String, target: String, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.bg)
            .border(1.dp, palette.edge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(source, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink)
        Text("→", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)
        Text(target, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Outlined.ExpandMore, null, tint = palette.ink3, modifier = Modifier.size(14.dp))
    }
}


// ── Chapter masthead (compact) ────────────────────────────────────────────

@Composable
internal fun ChapterMasthead(
    chapter: com.example.splitreader.domain.model.Chapter,
    chapterIndex: Int,
) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 32.dp, end = 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "— ${chapterIndex + 1} —",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
            color = palette.ink4,
        )
        if (!chapter.title.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = chapter.title,
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(48.dp).height(1.dp).background(palette.rule))
    }
}

// ── Status footer ─────────────────────────────────────────────────────────

@Composable
private fun ReaderStatusFooter(state: ReaderUiState.Success) {
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge
    val progress = if (state.book.chapters.isNotEmpty())
        (state.currentChapterIndex + 1).toFloat() / state.book.chapters.size else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(palette.bg2)
            .drawBehind {
                drawLine(edgeColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "CH ${state.currentChapterIndex + 1} OF ${state.book.chapters.size}",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
        Text("·", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink4)
        Text(
            text = "${state.sourceLanguage.badge} → ${state.targetLanguage.badge}",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
        Spacer(Modifier.weight(1f))
        // Progress rule + percentage
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.width(200.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(palette.bg3),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(1.dp)).background(palette.accent))
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
            )
        }
    }
}

// ── Translation banner ────────────────────────────────────────────────────

@Composable
private fun TranslationBanner(label: String, modifier: Modifier = Modifier, progress: Int? = null) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
        // Smoothly fill a thin bar as the percentage climbs, instead of the label
        // text jumping 0% → 15% → … with nothing in between.
        if (progress != null && progress > 0) {
            val animated by animateFloatAsState(
                targetValue = (progress / 100f).coerceIn(0f, 1f),
                animationSpec = tween(MotionTokens.Medium, easing = MotionTokens.EaseStandard),
                label = "translateProgress",
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.bg3),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.accent),
                )
            }
        }
    }
}

@Composable
private fun TranslationErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onOpenTranslator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = modifier
            .padding(top = 8.dp, start = 12.dp, end = 12.dp)
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Translation failed".uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = message,
            fontFamily = Newsreader,
            fontSize = 13.sp,
            color = palette.ink,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accent)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Retry",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.bg,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, palette.edge, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenTranslator)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Switch translator",
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    color = palette.ink2,
                )
            }
        }
    }
}

// ── Shared dialog shell ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorialDialog(
    eyebrow: String,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    AnimatedDialog(
        onDismiss = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) { dismiss ->
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.85f)
                .heightIn(max = maxDialogHeight)
                .clip(RoundedCornerShape(radii.xl))
                .background(palette.bg)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.xl)),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.xl, vertical = sp.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = eyebrow.uppercase(),
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = palette.ink3,
                    )
                    Text(
                        text = title,
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        color = palette.ink,
                    )
                }
                // 48dp touch target around the 32dp visual close circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = dismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.dp, palette.edge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Close", tint = palette.ink2, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))

            // Scrollable body — weighted so it scrolls within the capped dialog height
            // instead of pushing the last rows under the system gesture bar.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = sp.xl, vertical = sp.md)
                    .navigationBarsPadding(),
            ) {
                content()
            }
        }
    }
}

// ── Language picker dialog ────────────────────────────────────────────────

@Composable
private fun LanguagePickerDialog(
    currentTarget: Language,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

    EditorialDialog(eyebrow = "Translation", title = "Choose target language", onDismiss = onDismiss) {
        val langs = Language.entries
        val cols = 3
        val rows = (langs.size + cols - 1) / cols
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(cols) { col ->
                        val idx = row * cols + col
                        if (idx < langs.size) {
                            val lang = langs[idx]
                            val selected = lang == currentTarget
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(radii.md))
                                    .background(animatedSelection(if (selected) palette.ink else palette.bg2, "langPickerBg"))
                                    .border(1.dp, animatedSelection(if (selected) palette.ink else palette.edge, "langPickerBorder"), RoundedCornerShape(radii.md))
                                    .clickable { onSelect(lang) }
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (selected) palette.bg.copy(alpha = 0.22f) else palette.bg3)
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                    ) {
                                        Text(lang.badge, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (selected) palette.bg else palette.ink)
                                    }
                                    Text(lang.displayName, fontFamily = Newsreader, fontSize = 14.sp, color = if (selected) palette.bg else palette.ink)
                                    Text(lang.displayName, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 11.sp, color = if (selected) palette.bg.copy(alpha = 0.8f) else palette.ink3)
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(sp.md))

        // Info strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radii.md))
                .background(palette.bg2)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Language, null, tint = palette.ink3, modifier = Modifier.size(16.dp))
            Column {
                Row {
                    Text(stringResource(R.string.reader_ondevice_translation), fontFamily = Newsreader, fontSize = 12.sp, color = palette.ink2)
                    Text(stringResource(R.string.reader_no_cloud_calls), fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink2)
                }
                Text("ML KIT · DOWNLOADED", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.3.sp, color = palette.ink3)
            }
        }
        Spacer(Modifier.height(sp.sm))
    }
}

// ── Display settings dialog ───────────────────────────────────────────────

@Composable
private fun DisplaySettingsDialog(
    state: ReaderUiState.Success,
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
    wordHighlightEnabled: Boolean,
    onToggleWordHighlight: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

    EditorialDialog(eyebrow = "Reading", title = "Display", onDismiss = onDismiss) {
        // Theme swatches
        SectionEyebrow("Theme")
        Spacer(Modifier.height(sp.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(PaperPalette, SepiaPalette, NightPalette, AmoledPalette).forEach { p ->
                val selected = state.readerTheme == p.key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radii.md))
                        .background(p.bg)
                        .border(if (selected) 2.dp else 1.dp, animatedSelection(if (selected) p.ink else p.edge, "readerThemeSwatchBorder"), RoundedCornerShape(radii.md))
                        .clickable { onSetReaderTheme(p.key) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aa", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink)
                        Text(p.displayName.uppercase(), fontFamily = JetBrainsMono, fontSize = 11.sp, color = p.ink2)
                    }
                }
            }
        }

        Spacer(Modifier.height(sp.md))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        Spacer(Modifier.height(sp.md))

        // Typography (shared with the global Settings screen)
        TypographyControls(
            readingFont = state.readingFont,
            onSetReadingFont = onSetReadingFont,
            textSize = state.textSize,
            onSetTextSize = { onAdjustTextSize(it - state.textSize) },
            lineHeightMultiplier = state.lineHeightMultiplier,
            onSetLineHeight = { onAdjustLineHeight(it - state.lineHeightMultiplier) },
            letterSpacing = state.letterSpacing,
            onSetLetterSpacing = onSetLetterSpacing,
            textIndent = state.textIndent,
            onSetTextIndent = onSetTextIndent,
            paragraphSpacing = state.paragraphSpacing,
            onSetParagraphSpacing = onSetParagraphSpacing,
            justify = state.justifyText,
            onSetJustify = onSetJustifyText,
        )

        Spacer(Modifier.height(sp.md))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        Spacer(Modifier.height(sp.sm))

        // Toggles
        ToggleRow(
            label = "Show translation",
            sub = "Hide to read original only",
            checked = state.showTranslation,
            onToggle = onToggleTranslation,
        )
        Spacer(Modifier.height(sp.sm))
        ToggleRow(
            label = "Show illustrations",
            sub = "Display book images inline",
            checked = state.showIllustrations,
            onToggle = onToggleIllustrations,
        )
        Spacer(Modifier.height(sp.sm))
        ToggleRow(
            label = "Word highlight on tap",
            sub = "Show definition popover when long-pressing a word",
            checked = wordHighlightEnabled,
            onToggle = onToggleWordHighlight,
        )
        Spacer(Modifier.height(sp.sm))
    }
}

// ── Chapter picker dialog ─────────────────────────────────────────────────

@Composable
private fun ChapterPickerDialog(
    book: com.example.splitreader.domain.model.Book,
    currentChapterIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val edgeColor = palette.edge

    EditorialDialog(eyebrow = book.title, title = "Chapters", onDismiss = onDismiss) {
        book.chapters.forEachIndexed { index, chapter ->
            val isCurrent = index == currentChapterIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .drawBehind {
                        drawLine(edgeColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Roman numeral approximation
                Text(
                    text = toRoman(index + 1),
                    fontFamily = Newsreader,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    color = when {
                        isCurrent -> palette.accent
                        index < currentChapterIndex -> palette.ink3
                        else -> palette.ink2
                    },
                    modifier = Modifier.width(40.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = chapter.title ?: "Chapter ${index + 1}",
                        fontFamily = Newsreader,
                        fontSize = 15.sp,
                        color = if (index < currentChapterIndex) palette.ink3 else palette.ink,
                    )
                }
                when {
                    isCurrent -> Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.accentSoft)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("READING", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.accent, letterSpacing = 0.3.sp)
                    }
                    index < currentChapterIndex -> Icon(Icons.Outlined.Check, null, tint = palette.moss, modifier = Modifier.size(16.dp))
                    else -> Spacer(Modifier.width(24.dp))
                }
            }
        }
        Spacer(Modifier.height(sp.sm))
    }
}

// ── Bookmarks dialog ──────────────────────────────────────────────────────

@Composable
private fun BookmarksDialog(
    book: com.example.splitreader.domain.model.Book,
    bookmarks: List<Bookmark>,
    isCurrentBookmarked: Boolean,
    onToggleCurrent: () -> Unit,
    onRemove: (Int, Int) -> Unit,
    onJump: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val edgeColor = palette.edge

    EditorialDialog(eyebrow = book.title, title = "Bookmarks", onDismiss = onDismiss) {
        // Toggle the current reading position
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radii.md))
                .background(if (isCurrentBookmarked) palette.accentSoft else palette.bg2)
                .border(1.dp, if (isCurrentBookmarked) palette.accent else palette.edge, RoundedCornerShape(radii.md))
                .clickable(onClick = onToggleCurrent)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isCurrentBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    null,
                    tint = if (isCurrentBookmarked) palette.accent else palette.ink2,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (isCurrentBookmarked) "Remove bookmark here" else "Bookmark this page",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = palette.ink,
                )
            }
        }

        Spacer(Modifier.height(sp.md))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        Spacer(Modifier.height(sp.sm))

        if (bookmarks.isEmpty()) {
            Text(
                text = "No bookmarks yet. Tap “Bookmark this page” to add one.",
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = palette.ink3,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            bookmarks.forEach { bm ->
                val chapterTitle = book.chapters.getOrNull(bm.chapterIndex)?.title
                    ?: "Chapter ${bm.chapterIndex + 1}"
                val preview = book.chapters.getOrNull(bm.chapterIndex)
                    ?.paragraphs?.getOrNull(bm.paragraphIndex)
                    ?.take(80)?.trim().orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJump(bm.chapterIndex, bm.paragraphIndex) }
                        .drawBehind {
                            drawLine(edgeColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "$chapterTitle · ¶ ${bm.paragraphIndex + 1}",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            letterSpacing = 0.3.sp,
                            color = palette.ink3,
                        )
                        if (preview.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = preview,
                                fontFamily = Newsreader,
                                fontSize = 14.sp,
                                color = palette.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radii.sm))
                            .clickable { onRemove(bm.chapterIndex, bm.paragraphIndex) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = palette.ink3, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(sp.sm))
    }
}

private fun toRoman(num: Int): String {
    val numerals = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C",
        90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX",
        5 to "V", 4 to "IV", 1 to "I",
    )
    var n = num
    return buildString {
        for ((value, symbol) in numerals) {
            while (n >= value) { append(symbol); n -= value }
        }
    }
}

