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

