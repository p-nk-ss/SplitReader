package com.example.splitreader.screenshot

import com.example.splitreader.presentation.catalog.CatalogScreen
import com.example.splitreader.presentation.home.HomeScreen
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.words.WordsScreen
import org.junit.Test

/**
 * Golden screenshots for the three "library" screens (Home, Words, Catalog) across the base
 * matrix — palette {PAPER, NIGHT} x fontScale {1f, 1.3f} — plus one Words empty-state golden.
 * All screen callbacks are no-ops: these are pure rendering snapshots, not interaction tests.
 */
class LibraryScreensScreenshotTest : ScreenshotTest() {

    // ── Home ────────────────────────────────────────────────────────────────

    @Test
    fun home_paper_1x() = captureScreen("home_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    @Test
    fun home_night_1x() = captureScreen("home_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    @Test
    fun home_paper_13x() = captureScreen("home_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    @Test
    fun home_night_13x() = captureScreen("home_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    // ── Words ───────────────────────────────────────────────────────────────

    @Test
    fun words_paper_1x() = captureScreen("words_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        WordsScreen(
            words = ScreenFixtures.wordsRich,
            selectedWord = ScreenFixtures.wordsSelectedWord,
            langFilter = ScreenFixtures.wordsLangFilterAll,
            query = "",
            onSelectWord = {},
            onSetFilter = {},
            onSetQuery = {},
            onUpdateNote = { _, _ -> },
            onDelete = {},
            onSpeak = { _, _ -> },
        )
    }

    @Test
    fun words_night_1x() = captureScreen("words_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
        WordsScreen(
            words = ScreenFixtures.wordsRich,
            selectedWord = ScreenFixtures.wordsSelectedWord,
            langFilter = ScreenFixtures.wordsLangFilterAll,
            query = "",
            onSelectWord = {},
            onSetFilter = {},
            onSetQuery = {},
            onUpdateNote = { _, _ -> },
            onDelete = {},
            onSpeak = { _, _ -> },
        )
    }

    @Test
    fun words_paper_13x() = captureScreen("words_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
        WordsScreen(
            words = ScreenFixtures.wordsRich,
            selectedWord = ScreenFixtures.wordsSelectedWord,
            langFilter = ScreenFixtures.wordsLangFilterAll,
            query = "",
            onSelectWord = {},
            onSetFilter = {},
            onSetQuery = {},
            onUpdateNote = { _, _ -> },
            onDelete = {},
            onSpeak = { _, _ -> },
        )
    }

    @Test
    fun words_night_13x() = captureScreen("words_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
        WordsScreen(
            words = ScreenFixtures.wordsRich,
            selectedWord = ScreenFixtures.wordsSelectedWord,
            langFilter = ScreenFixtures.wordsLangFilterAll,
            query = "",
            onSelectWord = {},
            onSetFilter = {},
            onSetQuery = {},
            onUpdateNote = { _, _ -> },
            onDelete = {},
            onSpeak = { _, _ -> },
        )
    }

    @Test
    fun words_empty_paper_1x() = captureScreen(
        "words_empty_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
        WordsScreen(
            words = ScreenFixtures.wordsEmpty,
            selectedWord = null,
            langFilter = ScreenFixtures.wordsLangFilterAll,
            query = "",
            onSelectWord = {},
            onSetFilter = {},
            onSetQuery = {},
            onUpdateNote = { _, _ -> },
            onDelete = {},
            onSpeak = { _, _ -> },
        )
    }

    // ── Catalog ─────────────────────────────────────────────────────────────

    @Test
    fun catalog_paper_1x() = captureScreen("catalog_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        CatalogScreen(
            uiState = ScreenFixtures.catalogRich,
            driveState = ScreenFixtures.driveStateSignedOut,
            onQueryChange = {},
            onSourceSelected = {},
            onDownload = {},
            onRetry = {},
            onPickFromDrive = {},
            onSignInWithGoogle = {},
        )
    }

    @Test
    fun catalog_night_1x() = captureScreen("catalog_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
        CatalogScreen(
            uiState = ScreenFixtures.catalogRich,
            driveState = ScreenFixtures.driveStateSignedOut,
            onQueryChange = {},
            onSourceSelected = {},
            onDownload = {},
            onRetry = {},
            onPickFromDrive = {},
            onSignInWithGoogle = {},
        )
    }

    @Test
    fun catalog_paper_13x() = captureScreen("catalog_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
        CatalogScreen(
            uiState = ScreenFixtures.catalogRich,
            driveState = ScreenFixtures.driveStateSignedOut,
            onQueryChange = {},
            onSourceSelected = {},
            onDownload = {},
            onRetry = {},
            onPickFromDrive = {},
            onSignInWithGoogle = {},
        )
    }

    @Test
    fun catalog_night_13x() = captureScreen("catalog_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
        CatalogScreen(
            uiState = ScreenFixtures.catalogRich,
            driveState = ScreenFixtures.driveStateSignedOut,
            onQueryChange = {},
            onSourceSelected = {},
            onDownload = {},
            onRetry = {},
            onPickFromDrive = {},
            onSignInWithGoogle = {},
        )
    }
}
