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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListItemInfo
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.SaveWordResult
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.presentation.theme.isCompactWidth
import com.example.splitreader.presentation.theme.readerPalette
import kotlinx.coroutines.Job
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
            onAppearanceChange = viewModel::updateAppearance,
            onReadingPositionChanged = viewModel::onReadingPositionChanged,
            onMarkFinished = viewModel::markFinished,
            onToggleBookmark = viewModel::toggleBookmarkAtCurrentPosition,
            onRemoveBookmark = viewModel::removeBookmarkAt,
            onJumpToBookmark = viewModel::jumpToBookmark,
            onConsumePendingJump = viewModel::consumePendingJump,
            onVisibleRange = viewModel::onVisibleRange,
            onSaveWord = viewModel::saveWord,
            onSpeak = viewModel::speak,
            onSelectWord = viewModel::selectWord,
            onClearWordSelection = viewModel::clearWordSelection,
            onSelectionDragged = viewModel::updateWordSelectionRange,
            onSelectionDragEnd = viewModel::endWordSelectionDrag,
            onSelectProvider = viewModel::selectProvider,
            onConfigureProvider = viewModel::configureProvider,
            onClearProvider = viewModel::clearProvider,
            onRefreshTranslationUsage = viewModel::refreshTranslationUsage,
            onResetTranslationUsage = viewModel::resetTranslationUsage,
            onRetryTranslation = viewModel::retryTranslation,
            onTranslateWholeChapter = viewModel::translateWholeChapter,
            onDismissPortraitHint = viewModel::dismissPortraitHint,
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
internal fun ReaderContent(
    state: ReaderUiState.Success,
    onNavigateBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onAppearanceChange: ((ReadingAppearance) -> ReadingAppearance) -> Unit,
    onReadingPositionChanged: (ReadingPosition) -> Unit,
    onMarkFinished: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRemoveBookmark: (Int, Int) -> Unit,
    onJumpToBookmark: (Int, Int) -> Unit,
    onConsumePendingJump: () -> Unit,
    onVisibleRange: (Int, Int, Int, Int) -> Unit,
    onSaveWord: (String, Int, Int) -> Unit,
    onSpeak: (String, String) -> Unit,
    onSelectWord: (String, Int, Int, Int, Int) -> Unit,
    onClearWordSelection: () -> Unit,
    onSelectionDragged: (Int, Int) -> Unit,
    onSelectionDragEnd: () -> Unit,
    onSelectProvider: (TranslationProvider) -> Unit,
    onConfigureProvider: (TranslationProvider, String?, String?) -> Unit,
    onClearProvider: (TranslationProvider) -> Unit,
    onRefreshTranslationUsage: () -> Unit,
    onResetTranslationUsage: (TranslationProvider) -> Unit,
    onRetryTranslation: () -> Unit,
    onTranslateWholeChapter: () -> Unit,
    onDismissPortraitHint: (dontShowAgain: Boolean) -> Unit,
) {
    val appearance = state.appearance
    val palette = readerPalette(appearance.theme)

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
    val translationListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Exact item↔paragraph arithmetic (mastheads and illustration items are not paragraphs).
    val bookItemIndex = remember(state.book.chapters, appearance.showIllustrations) {
        BookItemIndex(state.book, appearance.showIllustrations)
    }
    // Long-lived effects below are keyed on listState only; read the index through this so an
    // illustrations toggle (which rebuilds the index) is seen by them, not the first-composition one.
    val currentBookItemIndex by rememberUpdatedState(bookItemIndex)

    // The single scroll-to-position path: book-load restore, bookmark jumps and the illustrations
    // toggle all arrive here. The jump keeps re-anchoring for a few seconds while rows settle (see
    // scrollToItemAndHold). It runs in the screen's scope, not this effect's: consuming the jump
    // changes this effect's key, which would cancel the hold the moment it started.
    val jumpHold = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(state.pendingJump, bookItemIndex) {
        val jump = state.pendingJump ?: return@LaunchedEffect
        val index = bookItemIndex.itemIndexOf(jump.chapter, jump.paragraph)
        jumpHold[0]?.cancel()
        jumpHold[0] = coroutineScope.launch { listState.scrollToItemAndHold(index, jump.offset) }
        onConsumePendingJump()
    }

    // Persist the Reading position (first at-least-partly-visible paragraph).
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(300)
            .collect { (globalIndex, offset) ->
                onReadingPositionChanged(currentBookItemIndex.positionAt(globalIndex, offset))
            }
    }

    // The end of the last chapter is on screen: the book is finished (Library shows 100%), and the
    // footer says 100% too — its paragraph count stops at the top line, 95–98% on the last screen.
    var bookEndVisible by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            BookEndView(info.visibleItemsInfo, info.totalItemsCount, info.viewportEndOffset, listState.canScrollBackward)
        }.collect { view ->
            val atEnd = bookEndAfter(bookEndVisible, view)
            if (atEnd && !bookEndVisible) onMarkFinished()
            bookEndVisible = atEnd
        }
    }

    // Report the *visible paragraph span* (first→last visible item, which may cross chapter
    // boundaries). ReaderTranslationEngine translates that window plus a bounded look-ahead/behind,
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
                val (startChapter, startPara) = currentBookItemIndex.paragraphAtOrAfter(firstIndex)
                val (endChapter, endPara) = currentBookItemIndex.paragraphAtOrBefore(lastIndex)
                onVisibleRange(startChapter, startPara, endChapter, endPara)
            }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPalette provides palette,
    ) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // "Vertical mode" is the single predicate the stacked layout, the split slider, and the
        // portrait hint all key off. Computed once here and threaded down so they can't disagree.
        val vertical = isCompactWidth(maxWidth) && appearance.showTranslation

        var hintShownThisEntry by rememberSaveable { mutableStateOf(false) }

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
                if (vertical) {
                    VerticalBookSpread(
                        modifier = Modifier.fillMaxSize(),
                        book = state.book,
                        chapterTranslations = state.chapterTranslations,
                        showIllustrations = appearance.showIllustrations,
                        verticalSplitRatio = appearance.verticalSplitRatio,
                        horizontalMargin = appearance.horizontalMargin,
                        style = state.readingStyle,
                        wordSelection = state.wordSelection,
                        wordHighlightEnabled = wordHighlightEnabled,
                        listState = listState,
                        translationListState = translationListState,
                        onWordSelected = { word, ch, para, start, end -> onSelectWord(word, ch, para, start, end) },
                        onSelectionDragged = onSelectionDragged,
                        onSelectionDragEnd = onSelectionDragEnd,
                        onSaveWord = onSaveWord,
                        onSpeak = onSpeak,
                        onDismiss = onClearWordSelection,
                        onToggleBars = { barsVisible = !barsVisible },
                        onSetVerticalSplitRatio = { r -> onAppearanceChange { it.copy(verticalSplitRatio = r) } },
                        barsVisible = barsVisible,
                        sourceLang = state.sourceLanguage,
                        targetLang = state.targetLanguage,
                    )
                } else {
                    BookSpread(
                        modifier = Modifier.fillMaxSize(),
                        book = state.book,
                        chapterTranslations = state.chapterTranslations,
                        showTranslation = appearance.showTranslation,
                        showIllustrations = appearance.showIllustrations,
                        splitRatio = appearance.splitRatio,
                        horizontalMargin = appearance.horizontalMargin,
                        style = state.readingStyle,
                        wordSelection = state.wordSelection,
                        wordHighlightEnabled = wordHighlightEnabled,
                        listState = listState,
                        onWordSelected = { word, ch, para, start, end -> onSelectWord(word, ch, para, start, end) },
                        onSelectionDragged = onSelectionDragged,
                        onSelectionDragEnd = onSelectionDragEnd,
                        onSaveWord = onSaveWord,
                        onSpeak = onSpeak,
                        onDismiss = onClearWordSelection,
                        onToggleBars = { barsVisible = !barsVisible },
                        onSetSplitRatio = { r -> onAppearanceChange { it.copy(splitRatio = r) } },
                        barsVisible = barsVisible,
                        sourceLang = state.sourceLanguage,
                        targetLang = state.targetLanguage,
                    )
                }

                // A book the parser found nothing showable in used to open as a blank page with no
                // explanation (found on a device). Images count as content when they are shown.
                val hasText = remember(state.book) { state.book.chapters.any { it.paragraphs.isNotEmpty() } }
                val hasImages = remember(state.book) { state.book.chapters.any { it.images.isNotEmpty() } }
                if (!hasText && !(hasImages && appearance.showIllustrations)) {
                    EmptyBookNotice(
                        imagesHidden = hasImages && !appearance.showIllustrations,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val downloading = state.translationState as? TranslationState.DownloadingModel
                    if (downloading != null) {
                        val pct = downloading.progress?.let { (it * 100).toInt() }
                        TranslationBanner(
                            label = if (pct == null) "Preparing translation…" else "Preparing translation… $pct%",
                        )
                    }
                    if (state.translationState is TranslationState.Translating) {
                        val progress = (state.translationState as TranslationState.Translating).progress
                        TranslationBanner(
                            label = if (progress <= 0) "Translating…" else "Translating… $progress%",
                            progress = progress,
                        )
                    }
                    if (state.translationState is TranslationState.Error) {
                        TranslationErrorBanner(
                            message = (state.translationState as TranslationState.Error).message,
                            onRetry = onRetryTranslation,
                            onOpenTranslator = { showTranslatorPicker = true },
                        )
                    } else if (barsVisible && appearance.showTranslation && state.translationNotice != null) {
                        // Shown with the bars (which auto-hide) so it never permanently covers the text.
                        TranslationFallbackStrip(
                            notice = state.translationNotice,
                            source = state.sourceLanguage,
                            target = state.targetLanguage,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ReaderStatusFooter(state = state, bookEndVisible = bookEndVisible)
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
                onAppearanceChange = onAppearanceChange,
                vertical = vertical,
                wordHighlightEnabled = wordHighlightEnabled,
                onToggleWordHighlight = { wordHighlightEnabled = !wordHighlightEnabled },
                onDismiss = { showDisplaySettings = false },
            )
        }
        if (vertical && !state.portraitHintDismissed && !hintShownThisEntry) {
            PortraitHintDialog(onDismiss = { dontShowAgain ->
                hintShownThisEntry = true
                onDismissPortraitHint(dontShowAgain)
            })
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
                onTranslateWholeChapter = if (appearance.showTranslation) onTranslateWholeChapter else null,
                onDismiss = { showTranslatorPicker = false },
            )
        }
        if (showChapterPicker) {
            ChapterPickerDialog(
                book = state.book,
                currentChapterIndex = state.currentChapterIndex,
                onSelect = { idx ->
                    onSelectChapter(idx)
                    coroutineScope.launch { listState.scrollToItem(bookItemIndex.chapterStartItem(idx)) }
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
                onJump = { ch, p -> onJumpToBookmark(ch, p); showBookmarks = false },
                onDismiss = { showBookmarks = false },
            )
        }
      }
    }
}

/** What the end-of-book check reads from the reader list's layout. */
internal data class BookEndView(
    val items: List<LazyListItemInfo>,
    val total: Int,
    val viewportEnd: Int,
    val scrolledDown: Boolean,
)

/**
 * Whether the reader is at the end of the book, given whether it was ([wasAtEnd]).
 *
 * It arrives when the bottom of the last row (the one before `end_padding`) is on screen after
 * scrolling — not merely its first pixel, or a long last paragraph read 100% with a screen to go.
 * It leaves only when that row is off screen: showing the reader's top bar shrinks the list from
 * the top and pushes the bottom of the last row out of view, which dropped the footer to 99% the
 * moment the reader opened the bars to look at it (found on a tablet).
 * `scrolledDown` guards against a book that fits one screen, or was merely opened.
 */
internal fun bookEndAfter(wasAtEnd: Boolean, view: BookEndView): Boolean {
    if (!view.scrolledDown || view.total < 2) return false
    val last = view.items.firstOrNull { it.index == view.total - 2 } ?: return false
    return wasAtEnd || last.offset + last.size <= view.viewportEnd
}
