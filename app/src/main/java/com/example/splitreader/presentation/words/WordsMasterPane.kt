package com.example.splitreader.presentation.words

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitreader.R
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.FadeInOnAppear
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.animatedSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// ── Master pane ──────────────────────────────────────────────────────────────

@Composable
internal fun MasterPane(
    words: List<SavedWord>,
    selectedWord: SavedWord?,
    langFilter: LangFilter,
    query: String,
    onSelectWord: (SavedWord) -> Unit,
    onSetFilter: (LangFilter) -> Unit,
    onSetQuery: (String) -> Unit,
    onDelete: (SavedWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current

    Column(modifier) {
        // Header
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.bg2)
                .padding(horizontal = sp.md, vertical = sp.lg)
        ) {
            Text(
                text = "Your",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = palette.ink3,
            )
            Text(
                text = stringResource(R.string.words_title),
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
                color = palette.ink,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${words.size} saved words",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = palette.ink3,
            )
        }

        // 1px separator
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))

        // Language filter pills
        val langs = remember(words) { words.map { it.sourceLang }.distinct().sorted() }
        LazyRow(
            Modifier
                .fillMaxWidth()
                .background(palette.bg2)
                .padding(horizontal = sp.sm, vertical = sp.xs),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                LangPill(
                    label = "All",
                    selected = langFilter is LangFilter.All,
                    onClick = { onSetFilter(LangFilter.All) },
                )
            }
            items(langs) { lang ->
                LangPill(
                    label = lang.uppercase(),
                    selected = langFilter is LangFilter.Lang && (langFilter as LangFilter.Lang).code == lang,
                    onClick = { onSetFilter(LangFilter.Lang(lang)) },
                )
            }
        }

        // Search field
        MasterSearchField(
            query = query,
            onQueryChange = onSetQuery,
        )

        // 1px separator
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))

        // Word list
        if (words.isEmpty()) {
            FadeInOnAppear(Modifier.weight(1f)) { EmptyMaster(Modifier.fillMaxSize()) }
        } else {
            val grouped = remember(words) {
                words.groupBy { relativeDate(it.savedAt) }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = sp.xs),
            ) {
                grouped.forEach { (dateLabel, group) ->
                    item(key = "header_$dateLabel") {
                        DateGroupHeader(dateLabel)
                    }
                    items(group, key = { it.id }) { word ->
                        Box(Modifier.animateItem()) {
                            WordListItem(
                                word = word,
                                selected = selectedWord?.id == word.id,
                                onSelect = { onSelectWord(word) },
                                onDelete = { onDelete(word) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LangPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    val bg = animatedSelection(if (selected) palette.accent else palette.bg3, "langPillBg")
    val fg = animatedSelection(if (selected) palette.bg else palette.ink2, "langPillFg")
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = fg,
        )
    }
}

@Composable
private fun MasterSearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(palette.bg2)
            .padding(horizontal = sp.md, vertical = sp.xs),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = Newsreader,
                fontSize = 14.sp,
                color = palette.ink,
            ),
            cursorBrush = SolidColor(palette.accent),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.bg3)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = palette.ink3,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search words…",
                                fontFamily = Newsreader,
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp,
                                color = palette.ink3,
                            )
                        }
                        inner()
                    }
                }
            },
        )
    }
}

@Composable
private fun DateGroupHeader(label: String) {
    val palette = LocalReaderPalette.current
    Text(
        text = label.uppercase(),
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = palette.ink3,
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bg2)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun WordListItem(
    word: SavedWord,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val bg = if (selected) palette.accentSoft else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = word.word,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = word.translation,
                    fontFamily = Newsreader,
                    fontSize = 12.sp,
                    color = palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.bg3)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = word.sourceLang.uppercase(),
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = palette.ink3,
                    )
                }
            }
        }

        // Delete swipe-alt: icon button (default 48dp touch target)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = palette.ink3,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(palette.edge),
    )
}

@Composable
private fun EmptyMaster(modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.StickyNote2,
            contentDescription = null,
            tint = palette.ink4,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(sp.sm))
        Text(
            "No saved words yet",
            fontFamily = Newsreader,
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            color = palette.ink3,
        )
        Spacer(Modifier.height(sp.xxs))
        Text(
            stringResource(R.string.words_empty_hint),
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.ink4,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )
    }
}
