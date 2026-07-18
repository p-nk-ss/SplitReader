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

// TODO(architecture): this file (~900 lines) holds the master list, the detail pane, and several
//  dialogs/cards. Consider splitting the master pane, detail pane, and dialogs into separate files.
//  No behavior change required.
@Composable
fun WordsRoute(viewModel: WordsViewModel = hiltViewModel()) {
    val words by viewModel.words.collectAsState()
    val selectedWord by viewModel.selectedWord.collectAsState()
    val langFilter by viewModel.langFilter.collectAsState()
    val query by viewModel.query.collectAsState()

    WordsScreen(
        words = words,
        selectedWord = selectedWord,
        langFilter = langFilter,
        query = query,
        onSelectWord = viewModel::select,
        onSetFilter = viewModel::setFilter,
        onSetQuery = viewModel::setQuery,
        onUpdateNote = viewModel::updateNote,
        onDelete = viewModel::delete,
        onSpeak = viewModel::speak,
    )
}

@Composable
fun WordsScreen(
    words: List<SavedWord>,
    selectedWord: SavedWord?,
    langFilter: LangFilter,
    query: String,
    onSelectWord: (SavedWord) -> Unit,
    onSetFilter: (LangFilter) -> Unit,
    onSetQuery: (String) -> Unit,
    onUpdateNote: (SavedWord, String) -> Unit,
    onDelete: (SavedWord) -> Unit,
    onSpeak: (String, String) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val context = LocalContext.current

    // Deletion goes through a confirmation dialog instead of removing the word immediately
    var wordPendingDelete by remember { mutableStateOf<SavedWord?>(null) }
    val requestDelete: (SavedWord) -> Unit = { wordPendingDelete = it }

    Row(Modifier.fillMaxSize()) {
        // Master pane — 380dp
        MasterPane(
            words = words,
            selectedWord = selectedWord,
            langFilter = langFilter,
            query = query,
            onSelectWord = onSelectWord,
            onSetFilter = onSetFilter,
            onSetQuery = onSetQuery,
            onDelete = requestDelete,
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(palette.bg2)
                .border(width = 1.dp, color = palette.edge, shape = RoundedCornerShape(0.dp)),
        )

        // Detail pane — fills remaining width
        DetailPane(
            word = selectedWord,
            onUpdateNote = onUpdateNote,
            onDelete = requestDelete,
            onSpeak = onSpeak,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(palette.bg),
        )
    }

    wordPendingDelete?.let { word ->
        AlertDialog(
            onDismissRequest = { wordPendingDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.word_delete_title),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = palette.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.word_delete_body, word.word),
                    fontFamily = Newsreader,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = palette.ink2,
                )
            },
            confirmButton = {
                val deletedMsg = stringResource(R.string.word_deleted)
                TextButton(onClick = {
                    onDelete(word)
                    wordPendingDelete = null
                    Toast.makeText(context, deletedMsg, Toast.LENGTH_SHORT).show()
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { wordPendingDelete = null }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontFamily = Newsreader,
                        fontSize = 14.sp,
                        color = palette.ink2,
                    )
                }
            },
            containerColor = palette.bg2,
            tonalElevation = 0.dp,
        )
    }
}

// ── Master pane ──────────────────────────────────────────────────────────────

@Composable
private fun MasterPane(
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

// ── Detail pane ──────────────────────────────────────────────────────────────

@Composable
private fun DetailPane(
    word: SavedWord?,
    onUpdateNote: (SavedWord, String) -> Unit,
    onDelete: (SavedWord) -> Unit,
    onSpeak: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (word == null) {
        FadeInOnAppear(modifier) { EmptyDetail(Modifier.fillMaxSize()) }
    } else {
        WordDetail(
            word = word,
            onUpdateNote = onUpdateNote,
            onDelete = onDelete,
            onSpeak = onSpeak,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptyDetail(modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = palette.ink4,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Select a word",
            fontFamily = Newsreader,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp,
            color = palette.ink3,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordDetail(
    word: SavedWord,
    onUpdateNote: (SavedWord, String) -> Unit,
    onDelete: (SavedWord) -> Unit,
    onSpeak: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val context = LocalContext.current
    val noteSavedMsg = stringResource(R.string.note_saved)
    var showNoteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(sp.xl),
        verticalArrangement = Arrangement.spacedBy(sp.lg),
    ) {
        // Word hero block
        item {
            Column {
                // Eyebrow
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = word.sourceLang.uppercase(),
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = palette.bg,
                        )
                    }
                    word.partOfSpeech?.let { pos ->
                        Text(
                            text = pos,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = palette.ink3,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Large italic word
                Text(
                    text = word.word,
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 64.sp,
                    color = palette.ink,
                    lineHeight = 68.sp,
                )

                Spacer(Modifier.height(sp.md))

                // Translation block
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.bg3)
                        .padding(sp.md),
                ) {
                    Column {
                        Text(
                            text = word.targetLang.uppercase(),
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = palette.ink3,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = word.translation,
                            fontFamily = Newsreader,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = palette.ink,
                            lineHeight = 26.sp,
                        )
                    }
                }
            }
        }

        // Context quote
        if (word.contextSnippet.isNotBlank()) {
            item {
                ContextQuoteCard(word)
            }
        }

        // Book info
        if (word.bookTitle.isNotBlank()) {
            item {
                BookInfoRow(word)
            }
        }

        // Action row
        item {
            ActionRow(
                onAddNote = { showNoteDialog = true },
                onPronounce = { onSpeak(word.word, word.sourceLang) },
                onDelete = { onDelete(word) },
            )
        }

        // Notes card
        word.note?.let { note ->
            if (note.isNotBlank()) {
                item {
                    NotesCard(
                        note = note,
                        onEdit = { showNoteDialog = true },
                    )
                }
            }
        }
    }

    if (showNoteDialog) {
        NoteDialog(
            initialNote = word.note ?: "",
            onDismiss = { showNoteDialog = false },
            onSave = { noteText ->
                onUpdateNote(word, noteText)
                showNoteDialog = false
                Toast.makeText(context, noteSavedMsg, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun ContextQuoteCard(word: SavedWord) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bg3),
    ) {
        // Accent left border
        Box(
            Modifier
                .width(3.dp)
                .height(80.dp)
                .background(palette.accent),
        )
        Column(Modifier.padding(sp.md)) {
            Text(
                text = "context",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = palette.ink3,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "\"${word.contextSnippet}\"",
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookInfoRow(word: SavedWord) {
    val palette = LocalReaderPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.MenuBook,
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = word.bookTitle,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "· ch. ${word.chapterIndex + 1}",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.ink4,
        )
    }
}

@Composable
private fun ActionRow(
    onAddNote: () -> Unit,
    onPronounce: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionChip(
            icon = Icons.Outlined.EditNote,
            label = "Add note",
            onClick = onAddNote,
            modifier = Modifier.weight(1f),
        )
        ActionChip(
            icon = Icons.Outlined.Headphones,
            label = "Pronounce",
            onClick = onPronounce,
            modifier = Modifier.weight(1f),
        )
        ActionChip(
            icon = Icons.Outlined.Delete,
            label = "Delete",
            onClick = onDelete,
            destructive = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val palette = LocalReaderPalette.current
    val fg = when {
        !enabled -> palette.ink4
        destructive -> MaterialTheme.colorScheme.error
        else -> palette.ink2
    }
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bg3)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = fg,
        )
    }
}

@Composable
private fun NotesCard(note: String, onEdit: () -> Unit) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, palette.edge, RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit)
            .padding(sp.md),
    ) {
        Text(
            text = "note",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = palette.ink3,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = note,
            fontFamily = Newsreader,
            fontSize = 14.sp,
            color = palette.ink,
            lineHeight = 20.sp,
        )
    }
}

// ── Note editing dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val palette = LocalReaderPalette.current
    var text by remember(initialNote) { mutableStateOf(initialNote) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.bg)
                .border(1.dp, palette.edge, RoundedCornerShape(18.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edit note",
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = palette.ink,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.bg3)
                    .border(1.dp, palette.edge, RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .height(120.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = Newsreader,
                        fontSize = 14.sp,
                        color = palette.ink,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxSize(),
                )
                if (text.isEmpty()) {
                    Text(
                        "Write your note…",
                        fontFamily = Newsreader,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        color = palette.ink3,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accent)
                        .clickable { onSave(text) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Save", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.bg)
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun relativeDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffDays = diffMs / (1000L * 60 * 60 * 24)
    return when {
        diffDays == 0L -> "Today"
        diffDays == 1L -> "Yesterday"
        diffDays < 7L -> "$diffDays days ago"
        diffDays < 30L -> "${diffDays / 7} weeks ago"
        else -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
