package com.example.splitreader.presentation.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeTranslationRepository
import com.example.splitreader.fakes.FakeTranslatorSettings
import com.example.splitreader.presentation.theme.SplitReaderTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The language picker's info strip said "ML KIT · DOWNLOADED" whatever the truth. It now says
 * whether ML Kit's models for the reader's pair are on the device — asked without downloading
 * anything ([com.example.splitreader.domain.repository.TranslationRepository.isReady]); unknown
 * (not asked yet, or an untranslatable book) shows no claim at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MlKitModelStatusTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "C", paragraphs = listOf("An English paragraph ".repeat(30)))),
    )

    private suspend fun TestScope.open(repo: FakeTranslationRepository): ReaderViewModelHarness {
        val h = ReaderViewModelHarness(
            book, prefs = FakeReadingPreferences(), repo = repo,
            settings = FakeTranslatorSettings(current = TranslationProvider.BERGAMOT),
            ioDispatcher = main.dispatcher,
        )
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        awaitLoaded(h)
        return h
    }

    private fun ReaderViewModelHarness.success() = vm.uiState.value as ReaderUiState.Success

    @Test
    fun `a pair whose ML Kit model is missing reads as not downloaded, and nothing is downloaded`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository()
        val h = open(repo)
        // After the open: the fake is provider-agnostic, and the book's own engine (Offline HQ)
        // "installs" English as it prepares the pair.
        repo.notInstalled += Language.ENGLISH
        val preparedBefore = repo.prepared.toList()

        h.vm.checkMlKitModel()

        assertTrue(pollUntil(5_000) { h.success().mlKitModelDownloaded != null })
        assertEquals(false, h.success().mlKitModelDownloaded)
        assertTrue("asked ML Kit", TranslationProvider.MLKIT in repo.readyChecks)
        assertEquals("checking downloads nothing", preparedBefore, repo.prepared.toList())
    }

    @Test
    fun `a pair whose ML Kit model is here reads as downloaded`() = runTest(main.dispatcher) {
        val h = open(FakeTranslationRepository())

        h.vm.checkMlKitModel()

        assertTrue(pollUntil(5_000) { h.success().mlKitModelDownloaded != null })
        assertEquals(true, h.success().mlKitModelDownloaded)
    }

    @Test
    fun `the strip says what the model state is`() {
        composeRule.setContent {
            SplitReaderTheme {
                LanguagePickerDialog(currentTarget = Language.RUSSIAN, onSelect = {}, onDismiss = {}, mlKitModelDownloaded = false)
            }
        }
        // assertExists: the strip sits below the fold of the dialog's scrolled body.
        composeRule.onNodeWithText("ML KIT · NOT DOWNLOADED").assertExists()
        composeRule.onNodeWithText("ML KIT · DOWNLOADED").assertDoesNotExist()
    }

    @Test
    fun `an unknown state makes no claim`() {
        composeRule.setContent {
            SplitReaderTheme {
                LanguagePickerDialog(currentTarget = Language.RUSSIAN, onSelect = {}, onDismiss = {}, mlKitModelDownloaded = null)
            }
        }
        composeRule.onNodeWithText("ML KIT · DOWNLOADED").assertDoesNotExist()
        composeRule.onNodeWithText("ML KIT · NOT DOWNLOADED").assertDoesNotExist()
    }
}
