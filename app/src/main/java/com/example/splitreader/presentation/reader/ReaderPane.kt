package com.example.splitreader.presentation.reader

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.R
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.MotionTokens
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.ShimmerBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.BreakIterator

// ── Page edge tap targets ─────────────────────────────────────────────────

@Composable
private fun PageEdgeTap(side: NavigationSide, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (side == NavigationSide.LEFT) "‹" else "›",
            fontFamily = Newsreader,
            fontSize = 22.sp,
            color = palette.ink3,
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

// ── Translation bubble (inline on translation side of selected paragraph) ──

@Composable
private fun TranslationBubble(
    wordSelection: WordSelection,
    onSave: () -> Unit,
    onSpeak: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val radii = LocalRadii.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.lg))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(radii.lg))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
            .padding(sp.md),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (wordSelection.selectionType == SelectionType.WORD) R.string.bubble_word
                    else R.string.bubble_sentence
                ),
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.accent,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = palette.ink3,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = wordSelection.word,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp,
            color = palette.ink,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        if (wordSelection.translation == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = palette.ink3,
                )
                Text(
                    text = stringResource(R.string.translating),
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = palette.ink3,
                )
            }
        } else {
            Text(
                text = wordSelection.translation,
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleChip(
                label = stringResource(R.string.action_listen),
                onClick = onSpeak,
                modifier = Modifier.weight(1f),
            )
            if (wordSelection.selectionType == SelectionType.WORD) {
                BubbleChip(
                    label = stringResource(R.string.action_save),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BubbleChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, palette.edge, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Newsreader,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
            color = palette.ink2,
        )
    }
}

// ── Book spread (continuous scroll — all chapters in one LazyColumn) ──────

@Composable
internal fun BookSpread(
    modifier: Modifier,
    book: com.example.splitreader.domain.model.Book,
    chapterTranslations: Map<Int, List<String>>,
    showTranslation: Boolean,
    showIllustrations: Boolean,
    splitRatio: Float,
    style: ReadingStyle,
    wordSelection: WordSelection?,
    wordHighlightEnabled: Boolean,
    listState: LazyListState,
    onWordSelected: (word: String, chapterIndex: Int, paragraphIndex: Int, start: Int, end: Int) -> Unit,
    onSelectionDragged: (start: Int, end: Int) -> Unit,
    onSaveWord: (word: String, chapterIndex: Int, paragraphIndex: Int) -> Unit,
    onSpeak: (text: String, langCode: String) -> Unit,
    onDismiss: () -> Unit,
    onToggleBars: () -> Unit,
    sourceLang: Language,
    targetLang: Language,
) {
    val palette = LocalReaderPalette.current
    val ruleColor = palette.rule

    Box(modifier = modifier) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(palette.bg)) {
        book.chapters.forEachIndexed { chapterIndex, chapter ->

            // Compact chapter masthead
            item(key = "masthead_$chapterIndex") {
                ChapterMasthead(chapter = chapter, chapterIndex = chapterIndex)
            }

            // Paragraph rows, with inline illustrations interleaved at their anchors. Each paragraph
            // item still uses `idx` (the paragraph index) for translation lookup, so alignment is
            // unchanged; images are separate full-width items that span both panes.
            val images = if (showIllustrations) chapter.images else emptyList()
            chapter.paragraphs.forEachIndexed { idx, original ->
                images.forEachIndexed { imgIdx, img ->
                    if (img.anchorParagraph == idx) {
                        item(key = "img_${chapterIndex}_$imgIdx") { Illustration(path = img.path) }
                    }
                }
                item(key = "p_${chapterIndex}_$idx") {
                val translated = if (showTranslation)
                    chapterTranslations[chapterIndex]?.getOrElse(idx) { "" } ?: ""
                else ""
                val awaitingTranslation = showTranslation && original.isNotBlank() && translated.isEmpty()
                val isSelected = wordSelection?.chapterIndex == chapterIndex && wordSelection.paragraphIndex == idx
                val (selectedStart, selectedEnd) = wordSelection
                    ?.takeIf { isSelected }
                    ?.let { it.startChar to it.endChar }
                    ?: (-1 to -1)

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
                        Box(
                            Modifier
                                .weight(1f - splitRatio)
                                .padding(start = 12.dp, end = 32.dp)
                                .alpha(if (wordSelection != null && !isSelected) 0.2f else 1f)
                        ) {
                            // Translation text always visible (dimmed when bubble is active).
                            // Crossfade so the skeleton placeholder eases into the resolved
                            // text instead of the translation snapping in at full opacity.
                            Box(Modifier.alpha(if (isSelected) 0.25f else 1f)) {
                                Crossfade(
                                    targetState = awaitingTranslation,
                                    animationSpec = tween(MotionTokens.Medium, easing = MotionTokens.EaseStandard),
                                    label = "translationResolve",
                                ) { awaiting ->
                                    if (awaiting) {
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
                        }
                    }
                } else {
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
                }
                Spacer(Modifier.height(style.paragraphSpacing.dp))
                }
            }
            // Illustrations anchored after the last paragraph
            images.forEachIndexed { imgIdx, img ->
                if (img.anchorParagraph >= chapter.paragraphs.size) {
                    item(key = "img_${chapterIndex}_$imgIdx") { Illustration(path = img.path) }
                }
            }
        }

        // End padding
        item(key = "end_padding") {
            Spacer(Modifier.height(48.dp))
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
 * Full-width inline illustration. Loads the bitmap off the main thread (downscaled to ~screen width
 * to avoid OOM on large plates) and spans both panes — images are not translated, so they are not
 * split. Renders nothing if the file is missing or undecodable (e.g. SVG), keeping the reader robust.
 */
@Composable
private fun Illustration(path: String) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }.toInt().coerceAtLeast(1)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > screenWidthPx * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { bmp ->
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/**
 * Shimmering skeleton shown in the translation column while a paragraph's
 * translation is still being fetched, so the pane reads as "loading" (rather than
 * blank or missing) and never appears empty on open.
 */
@Composable
private fun TranslationPlaceholder(
    style: ReadingStyle,
    modifier: Modifier = Modifier,
) {
    val lineHeight = (style.textSize * style.lineHeightMultiplier)
    val barHeight = (style.textSize * 0.78f).dp
    val shape = RoundedCornerShape(3.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((lineHeight - style.textSize * 0.78f).coerceAtLeast(4f).dp),
    ) {
        ShimmerBox(Modifier.fillMaxWidth().height(barHeight), shape)
        ShimmerBox(Modifier.fillMaxWidth().height(barHeight), shape)
        ShimmerBox(Modifier.fillMaxWidth(0.6f).height(barHeight), shape)
    }
}

@Composable
private fun ParagraphItem(
    text: String,
    index: Int,
    isFirstOfChapter: Boolean,
    isOriginal: Boolean,
    isActive: Boolean,
    selectedWordStart: Int,
    selectedWordEnd: Int,
    style: ReadingStyle,
    wordHighlightEnabled: Boolean = false,
    onWordSelected: (word: String, start: Int, end: Int) -> Unit,
    onSelectionDragged: (start: Int, end: Int) -> Unit = { _, _ -> },
    onTap: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnTap by rememberUpdatedState(newValue = onTap)
    val currentWordHighlightEnabled by rememberUpdatedState(newValue = wordHighlightEnabled)
    val baseColor = if (isOriginal) palette.ink else palette.ink2

    val hasSelection = isOriginal && selectedWordStart >= 0 && selectedWordEnd > selectedWordStart

    val wordIterator = remember(text) {
        if (text.isEmpty()) null else BreakIterator.getWordInstance().apply { setText(text) }
    }

    val currentSelStart by rememberUpdatedState(newValue = selectedWordStart)
    val currentSelEnd by rememberUpdatedState(newValue = selectedWordEnd)
    val currentOnDragged by rememberUpdatedState(newValue = onSelectionDragged)
    val currentOnWordSelected by rememberUpdatedState(newValue = onWordSelected)
    val currentLayoutForGesture by rememberUpdatedState(newValue = textLayoutResult)
    val currentIterForGesture by rememberUpdatedState(newValue = wordIterator)
    val handlesEnabled = isOriginal && isActive && hasSelection

    val handleColor = palette.accent
    val markerLineHeightPx: Float
    val markerCircleRadiusPx: Float
    val markerLineStrokePx: Float
    val markerHitRadiusPx: Float
    with(LocalDensity.current) {
        markerLineHeightPx = 12.dp.toPx()
        markerCircleRadiusPx = 6.dp.toPx()
        markerLineStrokePx = 2.dp.toPx()
        markerHitRadiusPx = 28.dp.toPx()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .pointerInput(isOriginal, text) {
                if (!isOriginal) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val downId = down.id

                    val layout = currentLayoutForGesture ?: return@awaitEachGesture
                    val selStart = currentSelStart
                    val selEnd = currentSelEnd
                    if (selStart < 0 || selEnd <= selStart || text.isEmpty()) return@awaitEachGesture

                    val (startAnchorX, startAnchorY) = handleAnchor(layout, text, selStart, isStart = true)
                    val startCenter = Offset(startAnchorX, startAnchorY + markerCircleRadiusPx + markerLineHeightPx / 2f)

                    val (endAnchorX, endAnchorY) = handleAnchor(layout, text, selEnd, isStart = false)
                    val endCenter = Offset(endAnchorX, endAnchorY + markerCircleRadiusPx + markerLineHeightPx / 2f)

                    val distStart = (downPos - startCenter).getDistance()
                    val distEnd = (downPos - endCenter).getDistance()
                    val side = when {
                        distStart < markerHitRadiusPx && distStart <= distEnd -> HandleSide.START
                        distEnd < markerHitRadiusPx -> HandleSide.END
                        else -> return@awaitEachGesture
                    }
                    down.consume()

                    var lastBoundary = if (side == HandleSide.START) selStart else selEnd

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == downId } ?: break
                        if (!change.pressed) break
                        change.consume()

                        val curLayout = currentLayoutForGesture ?: continue
                        val iter = currentIterForGesture ?: continue
                        val charIdx = curLayout.getOffsetForPosition(change.position).coerceIn(0, text.length)
                        val snapped = when (side) {
                            HandleSide.START -> snapToWordStart(charIdx, text, iter)
                            HandleSide.END -> snapToWordEnd(charIdx, text, iter)
                        }
                        val curOther = if (side == HandleSide.START) currentSelEnd else currentSelStart
                        val clamped = when (side) {
                            HandleSide.START -> snapped.coerceIn(0, (curOther - 1).coerceAtLeast(0))
                            HandleSide.END -> snapped.coerceIn((curOther + 1).coerceAtMost(text.length), text.length)
                        }
                        if (clamped != lastBoundary) {
                            lastBoundary = clamped
                            if (side == HandleSide.START) currentOnDragged(clamped, currentSelEnd)
                            else currentOnDragged(currentSelStart, clamped)
                        }
                    }
                }
            }
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = { offset ->
                        if (!currentWordHighlightEnabled) return@detectTapGestures
                        val layout = textLayoutResult ?: return@detectTapGestures
                        if (text.isEmpty()) return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)
                            .coerceIn(0, text.length - 1)
                        var start = charOffset
                        while (start > 0 && !text[start - 1].isWhitespace()) start--
                        var end = charOffset
                        while (end < text.length && !text[end].isWhitespace()) end++
                        while (start < end && !text[start].isLetterOrDigit()) start++
                        while (end > start && !text[end - 1].isLetterOrDigit()) end--
                        if (start < end) currentOnWordSelected(text.substring(start, end), start, end)
                    },
                )
            },
    ) {
        Text(
            text = buildAnnotatedString {
                if (hasSelection && selectedWordEnd <= text.length) {
                    if (selectedWordStart > 0) append(text.substring(0, selectedWordStart))
                    val midStyle = SpanStyle(background = palette.accentSoft, color = palette.accent)
                    val edgeStyle = midStyle.copy(textDecoration = TextDecoration.Underline)
                    val len = selectedWordEnd - selectedWordStart
                    when {
                        len <= 0 -> Unit
                        len == 1 -> withStyle(edgeStyle) {
                            append(text.substring(selectedWordStart, selectedWordEnd))
                        }
                        len == 2 -> {
                            withStyle(edgeStyle) {
                                append(text.substring(selectedWordStart, selectedWordStart + 1))
                            }
                            withStyle(edgeStyle) {
                                append(text.substring(selectedWordEnd - 1, selectedWordEnd))
                            }
                        }
                        else -> {
                            withStyle(edgeStyle) {
                                append(text.substring(selectedWordStart, selectedWordStart + 1))
                            }
                            withStyle(midStyle) {
                                append(text.substring(selectedWordStart + 1, selectedWordEnd - 1))
                            }
                            withStyle(edgeStyle) {
                                append(text.substring(selectedWordEnd - 1, selectedWordEnd))
                            }
                        }
                    }
                    if (selectedWordEnd < text.length) append(text.substring(selectedWordEnd))
                } else {
                    append(text)
                }
            },
            fontFamily = style.font.fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = style.textSize.sp,
            lineHeight = (style.textSize * style.lineHeightMultiplier).sp,
            letterSpacing = style.letterSpacing.sp,
            color = baseColor,
            textAlign = if (style.justify) TextAlign.Justify else TextAlign.Start,
            style = TextStyle(textIndent = TextIndent(firstLine = style.textIndent.sp)),
            onTextLayout = { textLayoutResult = it },
        )
    }
}