package com.example.splitreader.presentation.reader

import com.example.splitreader.presentation.theme.AnimatedDialog
import com.example.splitreader.presentation.theme.animatedSelection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.splitreader.R
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.ReadingFont
import com.example.splitreader.presentation.theme.AmoledPalette
import com.example.splitreader.presentation.theme.NightPalette
import com.example.splitreader.presentation.theme.PaperPalette
import com.example.splitreader.presentation.theme.SepiaPalette
import com.example.splitreader.presentation.ui.SectionEyebrow
import com.example.splitreader.presentation.ui.ToggleRow
import com.example.splitreader.presentation.ui.TypographyControls

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
internal fun LanguagePickerDialog(
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
internal fun DisplaySettingsDialog(
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
internal fun ChapterPickerDialog(
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
internal fun BookmarksDialog(
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
