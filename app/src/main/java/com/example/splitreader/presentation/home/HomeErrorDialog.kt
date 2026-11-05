package com.example.splitreader.presentation.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.Newsreader

/** A Library error (a book whose file has gone, an import that failed), styled like the delete dialog. */
@Composable
internal fun HomeErrorDialog(message: String, onDismiss: () -> Unit) {
    val palette = LocalReaderPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Can't open this book",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = palette.ink,
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "OK",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = palette.ink,
                )
            }
        },
        containerColor = palette.bg2,
        tonalElevation = 0.dp,
    )
}
