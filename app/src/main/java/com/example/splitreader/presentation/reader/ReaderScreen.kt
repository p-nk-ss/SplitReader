package com.example.splitreader.presentation.reader

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.PaperAccent
import com.example.splitreader.presentation.theme.PaperBg
import com.example.splitreader.presentation.theme.PaperEdge
import com.example.splitreader.presentation.theme.PaperInk
import com.example.splitreader.presentation.theme.PaperInk2
import com.example.splitreader.presentation.theme.PaperInk3
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.readerPalette
import com.example.splitreader.presentation.theme.AmoledPalette
import com.example.splitreader.presentation.theme.NightPalette
import com.example.splitreader.presentation.theme.PaperPalette
import com.example.splitreader.presentation.theme.SepiaPalette
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ── Entry point ───────────────────────────────────────────────────────────

@Composable
internal fun ReaderRoute(
    filePath: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(filePath) {
        viewModel.loadBook(Uri.parse(filePath))
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
            onSetSplitRatio = viewModel::setSplitRatio,
            onToggleTranslation = viewModel::toggleTranslation,
            onSetNavigationSide = viewModel::setNavigationSide,
            onSetHorizontalMargin = viewModel::setHorizontalMargin,
            onUpdateScrollPosition = viewModel::updateScrollPosition,
            onConsumeScrollRestore = viewModel::consumeScrollRestore,
            onEnsureChapterTranslated = viewModel::ensureChapterTranslated,
        )
    }
}

@Composable
private fun ReaderLoadingScreen() {
    Box(Modifier.fillMaxSize().background(PaperBg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PaperAccent)
    }
}

@Composable
private fun ReaderErrorScreen(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(PaperBg).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontFamily = Newsreader, fontSize = 16.sp, color = PaperInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(PaperInk).clickable(onClick = onBack).padding(12.dp, 8.dp)) {
                Text("Go back", fontFamily = Newsreader, fontStyle = FontStyle.Italic, color = PaperBg)
            }
        }
    }
}

// ── Reader content ────────────────────────────────────────────────────────

@Composable
private fun ReaderContent(
    state: ReaderUiState.Success,
    onNavigateBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onSetReaderTheme: (ReaderThemeKey) -> Unit,
    onAdjustTextSize: (Float) -> Unit,
    onAdjustLineHeight: (Float) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onToggleTranslation: () -> Unit,
    onSetNavigationSide: (NavigationSide) -> Unit,
    onSetHorizontalMargin: (Float) -> Unit,
    onUpdateScrollPosition: (Int, Int, Int) -> Unit,
    onConsumeScrollRestore: () -> Unit,
    onEnsureChapterTranslated: (Int) -> Unit,
) {
    val palette = readerPalette(state.readerTheme)

    var showLanguagePicker by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var activeParagraph by remember { mutableStateOf(-1 to -1) }  // (chapterIndex, paragraphIndex)

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Pre-compute global item start index for each chapter
    val chapterItemStarts = remember(state.book.chapters) {
        state.book.chapters.runningFold(0) { acc, ch -> acc + 1 + ch.paragraphs.size }.dropLast(1)
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

    // Preload translation for visible chapters as user scrolls
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { globalIndex ->
                val chapter = chapterItemStarts.indexOfLast { it <= globalIndex }.coerceAtLeast(0)
                onEnsureChapterTranslated(chapter)
                val next = (chapter + 1).coerceAtMost(state.book.chapters.size - 1)
                if (next != chapter) onEnsureChapterTranslated(next)
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
            ReaderTopBar(
                state = state,
                onBack = onNavigateBack,
                onOpenLanguagePicker = { showLanguagePicker = true },
                onOpenDisplaySettings = { showDisplaySettings = true },
                onOpenChapterPicker = { showChapterPicker = true },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                BookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = state.book,
                    chapterTranslations = state.chapterTranslations,
                    showTranslation = state.showTranslation,
                    splitRatio = state.splitRatio,
                    textSize = state.textSize,
                    lineHeightMultiplier = state.lineHeightMultiplier,
                    activeParagraph = activeParagraph,
                    listState = listState,
                    onLongPress = { chIdx, pIdx -> activeParagraph = chIdx to pIdx },
                    sourceLang = state.sourceLanguage,
                    targetLang = state.targetLanguage,
                )

                val (activeChapter, activePIdx) = activeParagraph
                if (activeChapter >= 0) {
                    ParagraphActionsOverlay(
                        paragraphIndex = activePIdx,
                        originalText = state.book.chapters.getOrNull(activeChapter)
                            ?.paragraphs?.getOrElse(activePIdx) { "" } ?: "",
                        onDismiss = { activeParagraph = -1 to -1 },
                    )
                }

                if (state.translationState is TranslationState.Translating) {
                    TranslationBanner(
                        progress = (state.translationState as TranslationState.Translating).progress,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }

            ReaderStatusFooter(state = state)
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
                onSetSplitRatio = onSetSplitRatio,
                onToggleTranslation = onToggleTranslation,
                onDismiss = { showDisplaySettings = false },
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
                withStyle(SpanStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = palette.ink3)) {
                    append(" · CH ${state.currentChapterIndex + 1}")
                }
            },
        )

        // Language chip
        LangChip(
            source = state.sourceLanguage.code.uppercase(),
            target = state.targetLanguage.code.uppercase(),
            onClick = onOpenLanguagePicker,
        )

        IconButton(onClick = onOpenDisplaySettings) {
            Icon(Icons.Outlined.TextFields, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onOpenChapterPicker) {
            Icon(Icons.Outlined.FormatListBulleted, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = {}) {
            Icon(Icons.Outlined.Bookmark, null, tint = palette.ink2, modifier = Modifier.size(20.dp))
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
        LangPill(source)
        Text("→", fontFamily = JetBrainsMono, fontSize = 10.sp, color = palette.ink4)
        LangPill(target)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Outlined.ExpandMore, null, tint = palette.ink3, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LangPill(code: String) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(palette.bg3)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(code, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = palette.ink)
    }
}

// ── Chapter masthead (compact) ────────────────────────────────────────────

@Composable
private fun ChapterMasthead(
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
            fontSize = 9.sp,
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

// ── Page edge tap targets ─────────────────────────────────────────────────

@Composable
private fun PageEdgeTap(side: NavigationSide, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (side == NavigationSide.LEFT) "‹" else "›",
            fontFamily = Newsreader,
            fontSize = 22.sp,
            color = palette.ink4,
        )
    }
}

// ── Page gutter ───────────────────────────────────────────────────────────

@Composable
private fun PageGutter(modifier: Modifier = Modifier.width(28.dp).fillMaxHeight()) {
    val palette = LocalReaderPalette.current
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Left fold gradient
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.06f), Color.Transparent),
                    startX = 0f, endX = size.width / 2f,
                ),
            )
            // Right fold gradient
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.06f)),
                    startX = size.width / 2f, endX = size.width,
                ),
            )
            // Center rule
            drawLine(
                color = palette.rule,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

// ── Book spread (continuous scroll — all chapters in one LazyColumn) ──────

@Composable
private fun BookSpread(
    modifier: Modifier,
    book: com.example.splitreader.domain.model.Book,
    chapterTranslations: Map<Int, List<String>>,
    showTranslation: Boolean,
    splitRatio: Float,
    textSize: Float,
    lineHeightMultiplier: Float,
    activeParagraph: Pair<Int, Int>,
    listState: LazyListState,
    onLongPress: (Int, Int) -> Unit,
    sourceLang: Language,
    targetLang: Language,
) {
    val palette = LocalReaderPalette.current
    val ruleColor = palette.rule

    LazyColumn(state = listState, modifier = modifier.background(palette.bg)) {
        book.chapters.forEachIndexed { chapterIndex, chapter ->

            // Compact chapter masthead
            item(key = "masthead_$chapterIndex") {
                ChapterMasthead(chapter = chapter, chapterIndex = chapterIndex)
            }

            // Page header row
            item(key = "header_$chapterIndex") {
                if (showTranslation) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Box(Modifier.weight(splitRatio).padding(start = 32.dp, end = 12.dp)) {
                            PageHeader(langCode = sourceLang.code.uppercase(), nativeName = sourceLang.displayName, isOriginal = true)
                        }
                        Spacer(Modifier.width(28.dp))
                        Box(Modifier.weight(1f - splitRatio).padding(start = 12.dp, end = 32.dp)) {
                            PageHeader(langCode = targetLang.code.uppercase(), nativeName = targetLang.displayName, isOriginal = false)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
                        PageHeader(langCode = sourceLang.code.uppercase(), nativeName = sourceLang.displayName, isOriginal = true)
                    }
                }
            }

            // Paragraph rows
            itemsIndexed(chapter.paragraphs, key = { idx, _ -> "p_${chapterIndex}_$idx" }) { idx, original ->
                val translated = if (showTranslation)
                    chapterTranslations[chapterIndex]?.getOrElse(idx) { "" } ?: ""
                else ""
                val isActive = activeParagraph == (chapterIndex to idx)

                if (showTranslation) {
                    Row(
                        modifier = Modifier.fillMaxWidth().drawBehind {
                            val gutterX = size.width * splitRatio
                            drawLine(ruleColor, Offset(gutterX, 0f), Offset(gutterX, size.height), 1.dp.toPx())
                        },
                    ) {
                        Box(Modifier.weight(splitRatio).padding(start = 32.dp, end = 12.dp)) {
                            ParagraphItem(
                                text = original,
                                index = idx,
                                isFirstOfChapter = idx == 0,
                                isOriginal = true,
                                isActive = isActive,
                                textSize = textSize,
                                lineHeightMultiplier = lineHeightMultiplier,
                                onLongPress = { onLongPress(chapterIndex, idx) },
                                onTap = {},
                            )
                        }
                        Box(Modifier.weight(1f - splitRatio).padding(start = 12.dp, end = 32.dp)) {
                            ParagraphItem(
                                text = translated,
                                index = idx,
                                isFirstOfChapter = false,
                                isOriginal = false,
                                isActive = isActive,
                                textSize = textSize,
                                lineHeightMultiplier = lineHeightMultiplier,
                                onLongPress = { onLongPress(chapterIndex, idx) },
                                onTap = {},
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                        ParagraphItem(
                            text = original,
                            index = idx,
                            isFirstOfChapter = idx == 0,
                            isOriginal = true,
                            isActive = isActive,
                            textSize = textSize,
                            lineHeightMultiplier = lineHeightMultiplier,
                            onLongPress = { onLongPress(chapterIndex, idx) },
                            onTap = {},
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        // End padding
        item(key = "end_padding") {
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Reader page ───────────────────────────────────────────────────────────

@Composable
private fun ReaderPage(
    modifier: Modifier,
    paragraphs: List<String>,
    isOriginal: Boolean,
    textSize: Float,
    lineHeightMultiplier: Float,
    activeParagraph: Int,
    listState: LazyListState,
    onLongPress: (Int) -> Unit,
    onTap: () -> Unit,
    langLabel: String = "",
    langNativeName: String = "",
) {
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(palette.bg)
            .padding(
                start = if (isOriginal) 64.dp else 56.dp,
                end = if (isOriginal) 56.dp else 64.dp,
                top = 20.dp,
                bottom = 24.dp,
            ),
    ) {
        // Page header
        item {
            PageHeader(
                langCode = if (isOriginal) "FR" else langLabel,
                nativeName = if (isOriginal) "Français" else langNativeName,
                isOriginal = isOriginal,
            )
            Spacer(Modifier.height(22.dp))
        }

        itemsIndexed(paragraphs) { index, paragraph ->
            val isActive = activeParagraph == index
            ParagraphItem(
                text = paragraph,
                index = index,
                isFirstOfChapter = index == 0 && isOriginal,
                isOriginal = isOriginal,
                isActive = isActive,
                textSize = textSize,
                lineHeightMultiplier = lineHeightMultiplier,
                onLongPress = onLongPress,
                onTap = onTap,
            )
            Spacer(Modifier.height(18.dp))
        }

        // Page footer (folio)
        item {
            PageFooter(isOriginal = isOriginal)
        }
    }
}

@Composable
private fun PageHeader(langCode: String, nativeName: String, isOriginal: Boolean) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOriginal) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isOriginal) {
            Text(
                text = "· TRANSLATION",
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
            )
            Spacer(Modifier.width(8.dp))
            Text(nativeName, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = palette.ink2)
            Spacer(Modifier.width(6.dp))
            LangPill(langCode)
        } else {
            LangPill(langCode)
            Spacer(Modifier.width(6.dp))
            Text(nativeName, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = palette.ink2)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "· ORIGINAL",
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
            )
        }
    }
}

@Composable
private fun ParagraphItem(
    text: String,
    index: Int,
    isFirstOfChapter: Boolean,
    isOriginal: Boolean,
    isActive: Boolean,
    textSize: Float,
    lineHeightMultiplier: Float,
    onLongPress: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val bgColor = when {
        isActive -> palette.accentSoft.copy(alpha = 0.55f)
        else     -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = if (isActive || bgColor != Color.Transparent) 6.dp else 0.dp)
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress(index) },
                )
            },
    ) {
        if (isFirstOfChapter && text.isNotEmpty()) {
            // Drop cap on first paragraph original side
            Row {
                Text(
                    text = text.first().toString(),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = (textSize * 3.6f).sp,
                    color = palette.accent,
                    lineHeight = (textSize * 3.6f).sp,
                    modifier = Modifier.alignBy { it.measuredHeight - it.measuredHeight / 4 },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = text.drop(1),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontSize = textSize.sp,
                    lineHeight = (textSize * lineHeightMultiplier).sp,
                    color = if (isOriginal) palette.ink else palette.ink2,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        } else {
            Text(
                text = text,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontSize = textSize.sp,
                lineHeight = (textSize * lineHeightMultiplier).sp,
                color = if (isOriginal) palette.ink else palette.ink2,
                textAlign = TextAlign.Justify,
            )
        }
    }
}

@Composable
private fun PageFooter(isOriginal: Boolean) {
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .drawBehind {
                drawLine(edgeColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .padding(top = 14.dp),
        horizontalArrangement = if (isOriginal) Arrangement.Start else Arrangement.End,
    ) {
        Text(
            text = if (isOriginal) "fr · reader" else "translation · reader",
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            letterSpacing = 0.3.sp,
            color = palette.ink4,
        )
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
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
        Text("·", fontFamily = JetBrainsMono, fontSize = 10.sp, color = palette.ink4)
        Text(
            text = "${state.sourceLanguage.code.uppercase()} → ${state.targetLanguage.code.uppercase()}",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
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
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
            )
        }
    }
}

// ── Translation banner ────────────────────────────────────────────────────

@Composable
private fun TranslationBanner(progress: Int, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Translating… $progress%",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
    }
}

// ── Paragraph actions overlay ─────────────────────────────────────────────

@Composable
private fun ParagraphActionsOverlay(
    paragraphIndex: Int,
    originalText: String,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current
    val word = originalText.split(" ").firstOrNull() ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 64.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(radii.lg))
                .background(palette.bg2)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.lg))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                .padding(sp.md),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED · ${palette.key.name}",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        color = palette.accent,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = word,
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        fontSize = 26.sp,
                        color = palette.ink,
                    )
                    Text(
                        text = "N. · definition",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        color = palette.ink3,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.dp, palette.edge, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, null, tint = palette.ink2, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.height(sp.sm))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
            Spacer(Modifier.height(sp.sm))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val actions = listOf(
                    Triple(Icons.Outlined.Style, "Save word", {}),
                    Triple(Icons.Outlined.BorderColor, "Highlight", {}),
                    Triple(Icons.Outlined.BookmarkBorder, "Bookmark", {}),
                    Triple(Icons.Outlined.EditNote, "Add note", {}),
                    Triple(Icons.Outlined.Headphones, "Listen", null as (() -> Unit)?),
                )
                actions.forEach { (icon, label, action) ->
                    val enabled = action != null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, palette.edge, RoundedCornerShape(8.dp))
                            .then(if (enabled) Modifier.clickable { action?.invoke() } else Modifier)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(icon, null, tint = if (enabled) palette.ink2 else palette.ink4, modifier = Modifier.size(18.dp))
                            Text(
                                text = label,
                                fontFamily = Newsreader,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp,
                                color = if (enabled) palette.ink2 else palette.ink4,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared dialog shell ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorialDialog(
    eyebrow: String,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(radii.xl))
                .background(palette.bg)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.xl))
                .shadow(elevation = 30.dp, shape = RoundedCornerShape(radii.xl)),
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
                        fontSize = 9.sp,
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
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, palette.edge, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, null, tint = palette.ink2, modifier = Modifier.size(16.dp))
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))

            // Scrollable body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = sp.xl, vertical = sp.md),
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
                                    .background(if (selected) palette.ink else palette.bg2)
                                    .border(1.dp, if (selected) palette.ink else palette.edge, RoundedCornerShape(radii.md))
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
                                        Text(lang.code.uppercase(), fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (selected) palette.bg else palette.ink)
                                    }
                                    Text(lang.displayName, fontFamily = Newsreader, fontSize = 14.sp, color = if (selected) palette.bg else palette.ink)
                                    Text(lang.displayName, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 10.sp, color = if (selected) palette.bg.copy(alpha = 0.8f) else palette.ink3)
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
                    Text("On-device translation · ", fontFamily = Newsreader, fontSize = 12.sp, color = palette.ink2)
                    Text("no cloud calls", fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink2)
                }
                Text("ML KIT · DOWNLOADED", fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 0.3.sp, color = palette.ink3)
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
    onSetSplitRatio: (Float) -> Unit,
    onToggleTranslation: () -> Unit,
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
                        .border(if (selected) 2.dp else 1.dp, if (selected) p.ink else p.edge, RoundedCornerShape(radii.md))
                        .clickable { onSetReaderTheme(p.key) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aa", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink)
                        Text(p.displayName.uppercase(), fontFamily = JetBrainsMono, fontSize = 8.sp, color = p.ink2)
                    }
                }
            }
        }

        Spacer(Modifier.height(sp.md))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        Spacer(Modifier.height(sp.md))

        // Font size slider
        SliderRow(
            label = "Font size",
            value = state.textSize,
            valueLabel = "${state.textSize.toInt()}sp",
            valueRange = 12f..24f,
            onValueChange = { onAdjustTextSize(it - state.textSize) },
        )

        Spacer(Modifier.height(sp.sm))

        // Line height slider
        SliderRow(
            label = "Line height",
            value = state.lineHeightMultiplier,
            valueLabel = "×${"%.2f".format(state.lineHeightMultiplier)}",
            valueRange = 1.20f..2.00f,
            onValueChange = { onAdjustLineHeight(it - state.lineHeightMultiplier) },
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
            label = "Word highlight on tap",
            sub = "Show definition popover when long-pressing a word",
            checked = true,
            onToggle = {},
        )
        Spacer(Modifier.height(sp.sm))
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    val palette = LocalReaderPalette.current
    Text(
        text = text.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 0.5.sp,
        color = palette.ink3,
    )
}

@Composable
private fun SliderRow(label: String, value: Float, valueLabel: String, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val palette = LocalReaderPalette.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = Newsreader, fontSize = 14.sp, color = palette.ink)
            Text(valueLabel, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 0.3.sp, color = palette.ink3)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(22.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.bg,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.bg3,
            ),
        )
    }
}

@Composable
private fun ToggleRow(label: String, sub: String, checked: Boolean, onToggle: () -> Unit) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = palette.ink)
            Text(sub, fontFamily = Newsreader, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = palette.ink3)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                uncheckedTrackColor = palette.bg3,
                checkedThumbColor = palette.bg,
                uncheckedThumbColor = palette.bg,
            ),
        )
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
                        Text("READING", fontFamily = JetBrainsMono, fontSize = 8.sp, color = palette.accent, letterSpacing = 0.3.sp)
                    }
                    index < currentChapterIndex -> Icon(Icons.Outlined.Check, null, tint = palette.moss, modifier = Modifier.size(16.dp))
                    else -> Spacer(Modifier.width(24.dp))
                }
            }
        }
        Spacer(Modifier.height(sp.sm))
    }
}

private fun toRoman(num: Int): String {
    val values = listOf(1000,900,500,400,100,90,50,40,10,9,5,4,1)
    val symbols = listOf("M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I")
    var n = num
    return buildString {
        for (i in values.indices) {
            while (n >= values[i]) { append(symbols[i]); n -= values[i] }
        }
    }
}

