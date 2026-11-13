package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ParagraphNote
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The translation pane's note on a paragraph in another language (2026-11-04). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h800dp-xhdpi")
class ParagraphNoteUiTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "C", paragraphs = listOf("English.", "Ein Brief.", "Arma virumque cano.", "Ein Gedicht."))),
    )

    private val spoken = mutableListOf<String>()

    private fun show(notes: ParagraphNotesUi, selection: WordSelection? = null) {
        composeRule.setContent {
            SplitReaderTheme {
                BookSpread(
                    modifier = Modifier.fillMaxSize(),
                    book = book,
                    chapterTranslations = mapOf(0 to listOf("Английский.", "Письмо.", "Arma virumque cano.", "Ein Gedicht.")),
                    showTranslation = true,
                    showIllustrations = true,
                    splitRatio = 0.5f,
                    horizontalMargin = 32f,
                    style = ScreenFixtures.readerContentState.readingStyle,
                    wordSelection = selection,
                    wordHighlightEnabled = true,
                    listState = rememberLazyListState(),
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSelectionDragEnd = {},
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, code -> spoken += code },
                    onDismiss = {},
                    onToggleBars = {},
                    onSetSplitRatio = {},
                    barsVisible = false,
                    sourceLang = Language.ENGLISH,
                    targetLang = Language.RUSSIAN,
                    paragraphNotes = notes,
                )
            }
        }
    }

    private val notes = mapOf(
        0 to mapOf(
            1 to ParagraphNote.TranslatedFrom(Language.GERMAN),
            2 to ParagraphNote.Original("la"),
            3 to ParagraphNote.NeedsModel(Language.GERMAN, TranslationProvider.BERGAMOT),
        ),
    )

    @Test
    fun `each paragraph in another language says what happened to it`() {
        show(ParagraphNotesUi(notes))
        composeRule.onNodeWithText("DE · from German").assertIsDisplayed()
        composeRule.onNodeWithText("LA · Latin, not translated").assertIsDisplayed()
        composeRule.onNodeWithText("DE · German, not translated").assertIsDisplayed()
    }

    @Test
    fun `the button asks for the paragraph's model, and shows the download while it runs`() {
        val asked = mutableListOf<Pair<Language, TranslationProvider>>()
        show(ParagraphNotesUi(notes, onDownload = { l, p -> asked += l to p }))
        composeRule.onNodeWithText("Download and translate", ignoreCase = true).performClick()
        assertEquals(listOf(Language.GERMAN to TranslationProvider.BERGAMOT), asked)
    }

    @Test
    fun `a running download shows its progress instead of the button`() {
        show(ParagraphNotesUi(notes, downloads = mapOf(Language.GERMAN to LanguageDownload.Running(0.4f))))
        composeRule.onNodeWithText("Downloading… 40%", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `a word's language is its paragraph's for every kind of note, the same for lookup, Save and Listen`() {
        val en = Language.ENGLISH
        val cases = listOf(
            null to "en",
            ParagraphNote.TranslatedFrom(Language.GERMAN) to "de",
            ParagraphNote.NeedsModel(Language.GERMAN, TranslationProvider.BERGAMOT) to "de",
            ParagraphNote.Original("ru") to "ru",
            ParagraphNote.Original("la") to "la",
            // Romanised: no voice and no dictionary of its own, the book's are used.
            ParagraphNote.Original("hi-latn") to "en",
        )
        assertEquals(cases.map { it.second }, cases.map { wordLanguageCode(it.first, "en") })
        // Save files the word under a language the app knows; an unsupported one falls back to the book's.
        assertEquals(
            listOf(en, Language.GERMAN, Language.GERMAN, Language.RUSSIAN, en, en),
            cases.map { wordLanguage(it.first, en) },
        )
        // Listen is the same function.
        cases.forEachIndexed { i, (note, code) ->
            val ui = ParagraphNotesUi(if (note == null) emptyMap() else mapOf(0 to mapOf(i to note)))
            assertEquals(code, ui.speechCode(0, i, "en"))
        }
    }

    @Test
    fun `romanised text is read with the book's voice, not a voice for another script`() {
        val ui = ParagraphNotesUi(mapOf(0 to mapOf(2 to ParagraphNote.Original("hi-latn"))))
        assertEquals("en", ui.speechCode(0, 2, "en"))
        assertEquals("la", ParagraphNotesUi(mapOf(0 to mapOf(2 to ParagraphNote.Original("la")))).speechCode(0, 2, "en"))
    }

    @Test
    fun `Listen on a word from a German paragraph speaks German`() {
        val brief = WordSelection("Brief", 0, 1, 4, 9, translation = SelectionTranslation.Ready("письмо"))
        show(ParagraphNotesUi(notes), selection = brief)
        composeRule.onNodeWithText("Listen").performClick()
        assertEquals(listOf("de"), spoken)
    }
}
