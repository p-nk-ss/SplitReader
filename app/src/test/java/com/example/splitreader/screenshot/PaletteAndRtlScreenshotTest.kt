package com.example.splitreader.screenshot

import com.example.splitreader.presentation.home.HomeScreen
import com.example.splitreader.presentation.reader.ReaderContent
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.words.WordsScreen
import org.junit.Test

/**
 * Spot goldens beyond the base PAPER/NIGHT x 1f/1.3f matrix: the two remaining palettes
 * (SEPIA, AMOLED) on a representative screen, and right-to-left layout (Arabic and other RTL
 * targets) on the two screens where mirroring matters most — the reading pane and a list/detail
 * screen. Callbacks are no-ops: pure rendering snapshots.
 *
 * RTL is driven by `captureScreen(rtl = true)`, which flips `LocalLayoutDirection` to
 * `LayoutDirection.Rtl` for the whole content tree.
 *
 * As in [ReadingScreensScreenshotTest], `ReaderContent` derives its reading-pane palette from
 * `state.readerTheme` (a per-book setting), independent of the ambient app-chrome theme — so the
 * RTL reader golden keeps the default PAPER reading palette while only the layout direction flips.
 */
class PaletteAndRtlScreenshotTest : ScreenshotTest() {

    /**
     * [ScreenFixtures.readerContentState] with chapter-1 translations added (the base fixture only
     * translates chapter 0) so `BookSpread` never falls back to the shimmering
     * `TranslationPlaceholder` for the second visible chapter.
     */
    private val readerFullyTranslated = ScreenFixtures.readerContentState.copy(
        chapterTranslations = ScreenFixtures.readerChapterTranslations + mapOf(
            1 to listOf(
                "Я запихнул рубашку или две в свой старый саквояж, сунул его под мышку и " +
                    "отправился к мысу Горн и в Тихий океан.",
                "Покинув добрый город старого Манхэтто, я благополучно прибыл в Нью-Бедфорд.",
            ),
        ),
    )

    // ── Palettes: SEPIA / AMOLED (Home) ───────────────────────────────────────

    @Test
    fun home_sepia_1x() = captureScreen("home_sepia_1x", theme = ReaderThemeKey.SEPIA, fontScale = 1f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    @Test
    fun home_amoled_1x() = captureScreen("home_amoled_1x", theme = ReaderThemeKey.AMOLED, fontScale = 1f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    // ── RTL layout (Reader, Words) ────────────────────────────────────────────

    @Test
    fun reader_rtl_1x() = captureScreen("reader_rtl_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f, rtl = true) {
        ReaderContent(
            state = readerFullyTranslated,
            onNavigateBack = {},
            onSelectChapter = {},
            onSetTargetLanguage = {},
            onSetReaderTheme = {},
            onAdjustTextSize = {},
            onAdjustLineHeight = {},
            onSetReadingFont = {},
            onSetLetterSpacing = {},
            onSetTextIndent = {},
            onSetParagraphSpacing = {},
            onSetJustifyText = {},
            onSetSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onUpdateScrollPosition = { _, _, _ -> },
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumeScrollRestore = {},
            onVisibleRange = { _, _, _, _ -> },
            onSaveWord = { _, _, _ -> },
            onSpeak = { _, _ -> },
            onSelectWord = { _, _, _, _, _ -> },
            onClearWordSelection = {},
            onSelectionDragged = { _, _ -> },
            onSelectProvider = {},
            onConfigureProvider = { _, _, _ -> },
            onClearProvider = {},
            onRefreshTranslationUsage = {},
            onResetTranslationUsage = {},
            onRetryTranslation = {},
            onTranslateWholeChapter = {},
        )
    }

    @Test
    fun words_rtl_1x() = captureScreen("words_rtl_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f, rtl = true) {
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
}
