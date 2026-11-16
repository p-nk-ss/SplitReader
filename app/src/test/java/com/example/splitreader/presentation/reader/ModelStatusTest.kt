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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The language picker's info strip said "ML KIT · DOWNLOADED" whatever the truth. It now says
 * whether the ACTIVE translator's on-device model for the reader's pair is here (Offline HQ — the
 * default — or ML Kit), asked without downloading anything
 * ([com.example.splitreader.domain.repository.TranslationRepository.isReady]). An online translator
 * has no model: no line. Unknown (not asked yet, an untranslatable book): no claim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ModelStatusTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        title = "T", author = "A", filePath = "/b",
        chapters = listOf(Chapter(index = 0, title = "C", paragraphs = listOf("An English paragraph ".repeat(30)))),
    )

    private suspend fun TestScope.open(repo: FakeTranslationRepository, provider: TranslationProvider): ReaderViewModelHarness {
        val h = ReaderViewModelHarness(
            book, prefs = FakeReadingPreferences(), repo = repo,
            settings = FakeTranslatorSettings(current = provider),
            ioDispatcher = main.dispatcher,
        )
        backgroundScope.launch { h.vm.uiState.collect {} }
        h.vm.loadBook(h.uri)
        awaitLoaded(h)
        return h
    }

    private fun ReaderViewModelHarness.success() = vm.uiState.value as ReaderUiState.Success

    @Test
    fun `Offline HQ without its pack reads as not downloaded, and nothing is downloaded`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository()
        val h = open(repo, TranslationProvider.BERGAMOT)
        // After the open: the fake is provider-agnostic, and the engine "installs" English as it
        // prepares the pair.
        repo.notInstalled += Language.ENGLISH
        val preparedBefore = repo.prepared.toList()
        repo.readyChecks.clear()

        h.vm.checkModel()

        assertTrue(pollUntil(5_000) { h.success().modelStatus != null })
        assertEquals(ModelStatus(TranslationProvider.BERGAMOT, downloaded = false), h.success().modelStatus)
        assertEquals("asked the active translator only", listOf(TranslationProvider.BERGAMOT), repo.readyChecks.distinct())
        assertEquals("checking downloads nothing", preparedBefore, repo.prepared.toList())
    }

    @Test
    fun `ML Kit with its model reads as downloaded`() = runTest(main.dispatcher) {
        val h = open(FakeTranslationRepository(), TranslationProvider.MLKIT)

        h.vm.checkModel()

        assertTrue(pollUntil(5_000) { h.success().modelStatus != null })
        assertEquals(ModelStatus(TranslationProvider.MLKIT, downloaded = true), h.success().modelStatus)
    }

    @Test
    fun `an online translator has no model to report`() = runTest(main.dispatcher) {
        val repo = FakeTranslationRepository()
        val h = open(repo, TranslationProvider.DEEPL)
        repo.readyChecks.clear()

        h.vm.checkModel()

        pollUntil(300) { h.success().modelStatus != null }
        assertNull(h.success().modelStatus)
        assertEquals(emptyList<TranslationProvider>(), repo.readyChecks.toList())
    }

    private fun showStrip(status: ModelStatus?) = composeRule.setContent {
        SplitReaderTheme {
            LanguagePickerDialog(currentTarget = Language.RUSSIAN, translatorProvider = status?.provider ?: TranslationProvider.BERGAMOT, onSelect = {}, onDismiss = {}, modelStatus = status)
        }
    }

    // assertExists, not assertIsDisplayed: the strip sits below the fold of the dialog's scrolled body.

    @Test
    fun `the strip names Offline HQ and its state`() {
        showStrip(ModelStatus(TranslationProvider.BERGAMOT, downloaded = false))
        composeRule.onNodeWithText("OFFLINE HQ · NOT DOWNLOADED").assertExists()
        composeRule.onNodeWithText("ML KIT", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the strip names ML Kit and its state`() {
        showStrip(ModelStatus(TranslationProvider.MLKIT, downloaded = true))
        composeRule.onNodeWithText("ML KIT · DOWNLOADED").assertExists()
    }

    @Test
    fun `an unknown state makes no claim`() {
        showStrip(null)
        composeRule.onNodeWithText("DOWNLOADED", substring = true).assertDoesNotExist()
    }
}
