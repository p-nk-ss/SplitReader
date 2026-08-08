package com.example.splitreader.presentation.reader

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.MotionTokens

/**
 * The stacked (portrait/narrow-window) layout: original above, translation below, a draggable
 * horizontal divider between. Used in place of [BookSpread] when
 * `isCompactWidth(maxWidth) && state.showTranslation` — see the branch in `ReaderContent`.
 *
 * There is deliberately no `showTranslation` parameter: the caller only reaches this composable
 * when translation is on, so accepting the flag here would create a second, unreachable
 * "translation off" path.
 *
 * The two [LazyColumn]s scroll independently — [listState] and [translationListState] are not
 * synchronized here. Both panes emit through the same [bookItems] structure, so their item
 * indices are identical by construction; that identity is what a future scroll-sync layer needs,
 * but wiring it up is out of scope for this composable.
 */
@Composable
internal fun VerticalBookSpread(
    modifier: Modifier,
    book: Book,
    chapterTranslations: Map<Int, List<String>>,
    showIllustrations: Boolean,
    verticalSplitRatio: Float,
    style: ReadingStyle,
    wordSelection: WordSelection?,
    wordHighlightEnabled: Boolean,
    listState: LazyListState,
    translationListState: LazyListState,
    onWordSelected: (word: String, chapterIndex: Int, paragraphIndex: Int, start: Int, end: Int) -> Unit,
    onSelectionDragged: (start: Int, end: Int) -> Unit,
    onSaveWord: (word: String, chapterIndex: Int, paragraphIndex: Int) -> Unit,
    onSpeak: (text: String, langCode: String) -> Unit,
    onDismiss: () -> Unit,
    onToggleBars: () -> Unit,
    onSetVerticalSplitRatio: (Float) -> Unit,
    barsVisible: Boolean,
    sourceLang: Language,
    targetLang: Language,
) {
    var dragRatio by remember { mutableFloatStateOf(Float.NaN) }
    val effectiveRatio = if (dragRatio.isNaN()) verticalSplitRatio else dragRatio
    var paneAreaHeightPx by remember { mutableIntStateOf(0) }
    val palette = LocalReaderPalette.current

    Box(modifier = modifier.onSizeChanged { paneAreaHeightPx = it.height }) {
        Column(Modifier.fillMaxSize().background(palette.bg)) {

            // Top pane — original, fully interactive.
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(effectiveRatio)) {
                bookItems(
                    book = book,
                    showIllustrations = showIllustrations,
                    masthead = { i, ch -> ChapterMasthead(chapter = ch, chapterIndex = i) },
                    image = { _, _, img -> Illustration(path = img.path) },
                    paragraph = { chapterIndex, idx, original ->
                        val isSelected = wordSelection?.chapterIndex == chapterIndex &&
                            wordSelection.paragraphIndex == idx
                        val (selectedStart, selectedEnd) = wordSelection
                            ?.takeIf { isSelected }
                            ?.let { it.startChar to it.endChar }
                            ?: (-1 to -1)
                        Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                            ParagraphItem(
                                text = original,
                                index = idx,
                                isFirstOfChapter = idx == 0,
                                isOriginal = true,
                                isActive = isSelected,
                                selectedWordStart = selectedStart,
                                selectedWordEnd = selectedEnd,
                                style = style,
                                wordHighlightEnabled = wordHighlightEnabled,
                                onWordSelected = { word, start, end -> onWordSelected(word, chapterIndex, idx, start, end) },
                                onSelectionDragged = { start, end -> onSelectionDragged(start, end) },
                                onTap = { if (wordSelection != null) onDismiss() else onToggleBars() },
                            )
                        }
                        Spacer(Modifier.height(style.paragraphSpacing.dp))
                    },
                )
            }

            HorizontalDividerHandle(
                ratio = effectiveRatio,
                paneAreaHeightPx = paneAreaHeightPx,
                prominent = barsVisible || !dragRatio.isNaN(),
                onDrag = { dragRatio = it },
                onDragFinished = {
                    if (!dragRatio.isNaN()) { onSetVerticalSplitRatio(dragRatio); dragRatio = Float.NaN }
                },
                onTap = onToggleBars,
            )

            // Bottom pane — translation, read-only.
            LazyColumn(
                state = translationListState,
                modifier = Modifier.fillMaxWidth().weight(1f - effectiveRatio),
            ) {
                bookItems(
                    book = book,
                    showIllustrations = showIllustrations,
                    masthead = { i, ch -> ChapterMasthead(chapter = ch, chapterIndex = i) },
                    // Parity item, not a re-decode: the index must exist in this pane or every
                    // later index shifts, but the picture itself is already on screen above.
                    image = { _, _, _ -> Spacer(Modifier.fillMaxWidth().height(24.dp)) },
                    paragraph = { chapterIndex, idx, original ->
                        val translated = chapterTranslations[chapterIndex]?.getOrElse(idx) { "" } ?: ""
                        val awaiting = original.isNotBlank() && translated.isEmpty()
                        val isSelected = wordSelection?.chapterIndex == chapterIndex &&
                            wordSelection.paragraphIndex == idx
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .alpha(if (wordSelection != null && !isSelected) 0.2f else 1f)
                        ) {
                            Crossfade(
                                targetState = awaiting,
                                animationSpec = tween(MotionTokens.Medium, easing = MotionTokens.EaseStandard),
                                label = "translationResolve",
                            ) { isAwaiting ->
                                if (isAwaiting) {
                                    TranslationPlaceholder(style = style)
                                } else {
                                    ParagraphItem(
                                        text = translated,
                                        index = idx,
                                        isFirstOfChapter = idx == 0,
                                        isOriginal = false,
                                        isActive = false,
                                        selectedWordStart = -1,
                                        selectedWordEnd = -1,
                                        style = style,
                                        onWordSelected = { _, _, _ -> },
                                        onTap = { if (wordSelection != null) onDismiss() else onToggleBars() },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(style.paragraphSpacing.dp))
                    },
                )
            }
        }

        if (wordSelection != null) {
            TranslationBubble(
                wordSelection = wordSelection,
                onSave = { onSaveWord(wordSelection.word, wordSelection.chapterIndex, wordSelection.paragraphIndex) },
                onSpeak = { onSpeak(wordSelection.word, sourceLang.code) },
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

/**
 * The stacked layout's divider: a real 28dp row between the two panes, not an overlay. Unlike the
 * landscape [DividerHandle], nothing scrolls underneath it, so it does not need to forward
 * vertical drags into a list — the vertical drag here *is* the ratio gesture.
 */
@Composable
private fun HorizontalDividerHandle(
    ratio: Float,
    paneAreaHeightPx: Int,
    prominent: Boolean,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onTap: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val gripAlpha by animateFloatAsState(
        targetValue = if (prominent) 0.9f else 0.25f,
        label = "verticalDividerHandleAlpha",
    )
    val currentRatio by rememberUpdatedState(ratio)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) { detectTapGestures { currentOnTap() } }
            .pointerInput(paneAreaHeightPx) {
                detectVerticalDragGestures(
                    onDragEnd = { currentOnDragFinished() },
                    onDragCancel = { currentOnDragFinished() },
                ) { _, dy ->
                    if (paneAreaHeightPx > 0) {
                        currentOnDrag(
                            DividerDragMath.newVerticalRatio(currentRatio, dy, paneAreaHeightPx.toFloat())
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .alpha(gripAlpha)
                .background(palette.rule, RoundedCornerShape(2.dp))
        )
    }
}
