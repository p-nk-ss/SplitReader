package com.example.splitreader.presentation.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.example.splitreader.presentation.theme.MotionTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.Newsreader

// ── Top bar ───────────────────────────────────────────────────────────────

@Composable
internal fun ReaderTopBar(
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
internal fun ReaderStatusFooter(state: ReaderUiState.Success) {
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
internal fun TranslationBanner(label: String, modifier: Modifier = Modifier, progress: Int? = null) {
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
internal fun TranslationErrorBanner(
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
