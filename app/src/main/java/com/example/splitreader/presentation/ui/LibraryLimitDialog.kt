package com.example.splitreader.presentation.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.splitreader.R
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase.Companion.FREE_BOOK_LIMIT
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.Newsreader

/**
 * Shown when a free-tier user tries to add a book past [FREE_BOOK_LIMIT]. Offers to upgrade
 * (Phase 1: placeholder) or dismiss and free up a slot by deleting an existing book.
 */
@Composable
fun LibraryLimitDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.library_limit_title),
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = palette.ink,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.library_limit_body, FREE_BOOK_LIMIT),
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
            )
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text(
                    text = stringResource(R.string.library_limit_upgrade),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = palette.ink,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.library_limit_dismiss),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = palette.ink2,
                )
            }
        },
    )
}
