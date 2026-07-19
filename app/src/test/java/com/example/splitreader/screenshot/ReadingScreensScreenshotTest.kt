package com.example.splitreader.screenshot

import com.example.splitreader.presentation.almanac.AlmanacScreen
import com.example.splitreader.presentation.reader.ReaderContent
import com.example.splitreader.presentation.theme.ReaderThemeKey
import org.junit.Test

/**
 * Golden screenshots for the two "reading" screens (Almanac, ReaderContent) across the base
 * matrix — palette {PAPER, NIGHT} x fontScale {1f, 1.3f}. All screen callbacks are no-ops: these
 * are pure rendering snapshots, not interaction tests.
 *
 * [ScreenFixtures.readerContentState] is a "settled" `ReaderUiState.Success` (translationState =
 * Idle, no active word selection, no dialogs open) so `ReaderContent` renders without an active
 * translation/loading banner. The screen's finite entry animations (bar auto-hide slide/fade,
 * range-selector color) resolve during `captureScreen`'s `waitForIdle()`.
 *
 * Note: `ReaderContent` derives its reading-pane palette from `state.readerTheme`, not from the
 * ambient `SplitReaderTheme` that `captureScreen`'s `theme` param wraps around the content (the
 * reader's palette is a per-book setting, independent of the app-chrome theme). So the NIGHT
 * variants below use `readerFullyTranslated.copy(readerTheme = NIGHT)` — passing only
 * `theme = NIGHT` to `captureScreen` would leave the reading pane itself rendered in PAPER.
 *
 * Also note: [ScreenFixtures.readerChapterTranslations] only covers chapter 0. `BookSpread`
 * (`ReaderPane.kt`) renders any paragraph missing from that map via `TranslationPlaceholder` — a
 * shimmer built on `rememberInfiniteTransition` (`Motion.kt`) — which is exactly the kind of
 * infinite animation the base harness's fixtures are meant to avoid. Since the fixture book has 2
 * chapters, [readerFullyTranslated] below adds chapter-1 translations so both visible chapters are
 * fully resolved and no shimmer renders in the golden.
 */
class ReadingScreensScreenshotTest : ScreenshotTest() {

    /**
     * [ScreenFixtures.readerContentState] with chapter-1 translations added (the base fixture only
     * translates chapter 0) so `BookSpread` never falls back to the shimmering
     * `TranslationPlaceholder` for the second chapter visible in the fixture book.
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

    // ── Almanac ─────────────────────────────────────────────────────────────

    @Test
    fun almanac_paper_1x() = captureScreen("almanac_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        AlmanacScreen(
            streak = ScreenFixtures.almanacRich.streak,
            dailyMinutes = ScreenFixtures.almanacRich.dailyMinutes,
            rangeMinutes = ScreenFixtures.almanacRich.rangeMinutes,
            rangePages = ScreenFixtures.almanacRich.rangePages,
            rangeWords = ScreenFixtures.almanacRich.rangeWords,
            timeByBook = ScreenFixtures.almanacRich.timeByBook,
            timeByLang = ScreenFixtures.almanacRich.timeByLang,
            selectedRange = ScreenFixtures.almanacRich.selectedRange,
            onSelectRange = {},
        )
    }

    @Test
    fun almanac_night_1x() = captureScreen("almanac_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
        AlmanacScreen(
            streak = ScreenFixtures.almanacRich.streak,
            dailyMinutes = ScreenFixtures.almanacRich.dailyMinutes,
            rangeMinutes = ScreenFixtures.almanacRich.rangeMinutes,
            rangePages = ScreenFixtures.almanacRich.rangePages,
            rangeWords = ScreenFixtures.almanacRich.rangeWords,
            timeByBook = ScreenFixtures.almanacRich.timeByBook,
            timeByLang = ScreenFixtures.almanacRich.timeByLang,
            selectedRange = ScreenFixtures.almanacRich.selectedRange,
            onSelectRange = {},
        )
    }

    @Test
    fun almanac_paper_13x() = captureScreen("almanac_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
        AlmanacScreen(
            streak = ScreenFixtures.almanacRich.streak,
            dailyMinutes = ScreenFixtures.almanacRich.dailyMinutes,
            rangeMinutes = ScreenFixtures.almanacRich.rangeMinutes,
            rangePages = ScreenFixtures.almanacRich.rangePages,
            rangeWords = ScreenFixtures.almanacRich.rangeWords,
            timeByBook = ScreenFixtures.almanacRich.timeByBook,
            timeByLang = ScreenFixtures.almanacRich.timeByLang,
            selectedRange = ScreenFixtures.almanacRich.selectedRange,
            onSelectRange = {},
        )
    }

    @Test
    fun almanac_night_13x() = captureScreen("almanac_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
        AlmanacScreen(
            streak = ScreenFixtures.almanacRich.streak,
            dailyMinutes = ScreenFixtures.almanacRich.dailyMinutes,
            rangeMinutes = ScreenFixtures.almanacRich.rangeMinutes,
            rangePages = ScreenFixtures.almanacRich.rangePages,
            rangeWords = ScreenFixtures.almanacRich.rangeWords,
            timeByBook = ScreenFixtures.almanacRich.timeByBook,
            timeByLang = ScreenFixtures.almanacRich.timeByLang,
            selectedRange = ScreenFixtures.almanacRich.selectedRange,
            onSelectRange = {},
        )
    }

    // ── ReaderContent ───────────────────────────────────────────────────────

    @Test
    fun reader_paper_1x() = captureScreen("reader_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
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
    fun reader_night_1x() = captureScreen("reader_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
        ReaderContent(
            state = readerFullyTranslated.copy(readerTheme = ReaderThemeKey.NIGHT),
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
    fun reader_paper_13x() = captureScreen("reader_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
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
    fun reader_night_13x() = captureScreen("reader_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
        ReaderContent(
            state = readerFullyTranslated.copy(readerTheme = ReaderThemeKey.NIGHT),
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
}
