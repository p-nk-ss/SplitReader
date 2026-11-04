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

    private fun show(notes: ParagraphNotesUi) {
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
                    wordSelection = null,
                    wordHighlightEnabled = true,
                    listState = rememberLazyListState(),
                    onWordSelected = { _, _, _, _, _ -> },
                    onSelectionDragged = { _, _ -> },
                    onSelectionDragEnd = {},
                    onSaveWord = { _, _, _ -> },
                    onSpeak = { _, _ -> },
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
}
