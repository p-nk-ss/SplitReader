package com.example.splitreader.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ParagraphNote
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.ui.LibraryTagButton

/** What the translation panes need to show paragraphs in another language than the book's. */
@Immutable
internal data class ParagraphNotesUi(
    /** chapter → paragraph → note ([ReaderUiState.Success.paragraphNotes]). */
    val notes: Map<Int, Map<Int, ParagraphNote>> = emptyMap(),
    val downloads: Map<Language, LanguageDownload> = emptyMap(),
    val onDownload: (Language, TranslationProvider) -> Unit = { _, _ -> },
) {
    fun noteAt(chapter: Int, paragraph: Int): ParagraphNote? = notes[chapter]?.get(paragraph)

    /** The language to read a word of this paragraph aloud in: its own when noted, else [bookCode]. */
    fun speechCode(chapter: Int, paragraph: Int, bookCode: String): String = when (val n = noteAt(chapter, paragraph)) {
        is ParagraphNote.TranslatedFrom -> n.language.code
        is ParagraphNote.NeedsModel -> n.language.code
        is ParagraphNote.Original -> n.code.substringBefore('-')
        null -> bookCode
    }
}

/**
 * A one-line note above a paragraph's translation when the paragraph is in another language: where
 * the translation came from ("DE · from German"), why the original is shown ("LA · Latin, not
 * translated"), or — when an on-device model is missing — a button that downloads it. The download
 * only ever starts from this button: a pack can be tens of MB, and the reader may be on mobile data.
 */
@Composable
internal fun ParagraphNoteLine(
    note: ParagraphNote,
    target: Language,
    download: LanguageDownload?,
    onDownload: (Language, TranslationProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    Column(modifier.padding(bottom = 4.dp).testTag(PARAGRAPH_NOTE_TAG), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            noteLabel(note, target),
            fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3,
        )
        if (note is ParagraphNote.NeedsModel) {
            val text = when (download) {
                is LanguageDownload.Running -> download.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…"
                LanguageDownload.Failed -> "Download failed — retry"
                null -> "Download and translate"
            }
            LibraryTagButton(
                text = text,
                onClick = { if (download !is LanguageDownload.Running) onDownload(note.language, note.provider) },
                showPlus = false, dense = true, filled = download == null,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

internal fun noteLabel(note: ParagraphNote, target: Language): String = when (note) {
    is ParagraphNote.TranslatedFrom -> "${note.language.badge} · from ${languageName(note.language.code)}"
    is ParagraphNote.Original ->
        if (note.code == target.code) "${target.badge} · original"
        else "${note.code.substringBefore('-').uppercase()} · ${languageName(note.code)}, not translated"
    is ParagraphNote.NeedsModel -> "${note.language.badge} · ${languageName(note.language.code)}, not translated"
}

/**
 * What the word popup says on a paragraph shown as it is ([note]), where it looks nothing up: why,
 * and — for a missing model — where to get it. Null for a paragraph that is translated.
 */
internal fun popupReason(note: ParagraphNote?, target: Language): String? = when (note) {
    null, is ParagraphNote.TranslatedFrom -> null
    is ParagraphNote.NeedsModel ->
        "${languageName(note.language.code)} paragraph — tap Download and translate above its translation"
    is ParagraphNote.Original ->
        if (note.code == target.code) "Already in ${languageName(target.code)}"
        else "${languageName(note.code)} can't be translated yet"
}

/** Test tag on [ParagraphNoteLine]. */
internal const val PARAGRAPH_NOTE_TAG = "paragraph_note"
