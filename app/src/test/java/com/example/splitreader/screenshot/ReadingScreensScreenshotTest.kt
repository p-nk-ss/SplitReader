package com.example.splitreader.screenshot

import com.example.splitreader.presentation.almanac.AlmanacScreen
import com.example.splitreader.presentation.reader.DisplaySettingsDialog
import com.example.splitreader.presentation.reader.ReaderContent
import com.example.splitreader.presentation.reader.WordSelection
import com.example.splitreader.presentation.theme.ReaderThemeKey
import org.junit.Test
import org.robolectric.annotation.Config

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
    // `portraitHintDismissed = true` so the existing goldens below (written before the hint
    // existed) don't pick up an unrequested dialog overlay; `reader_vertical_hint_paper_1x`
    // below is the one golden that flips it back to `false` on purpose.
    private val readerFullyTranslated = ScreenFixtures.readerContentState.copy(
        chapterTranslations = ScreenFixtures.readerChapterTranslations + mapOf(
            1 to listOf(
                "Я запихнул рубашку или две в свой старый саквояж, сунул его под мышку и " +
                    "отправился к мысу Горн и в Тихий океан.",
                "Покинув добрый город старого Манхэтто, я благополучно прибыл в Нью-Бедфорд.",
            ),
        ),
        portraitHintDismissed = true,
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
            today = ScreenFixtures.almanacRich.today,
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
            today = ScreenFixtures.almanacRich.today,
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
            today = ScreenFixtures.almanacRich.today,
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
            today = ScreenFixtures.almanacRich.today,
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    /** Non-default split (35/65): pane weights and the divider handle must follow the ratio. */
    @Test
    fun reader_split035_paper_1x() = captureScreen("reader_split035_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        ReaderContent(
            state = readerFullyTranslated.copy(splitRatio = 0.35f),
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    // ── Vertical (stacked) layout — narrow window ─────────────────────────────
    //
    // The class-level @Config renders at TABLET width, so every test below carries a per-method
    // qualifier override. Without it, `isCompactWidth(maxWidth)` would be false and the capture
    // would silently exercise the side-by-side `BookSpread` arm instead of `VerticalBookSpread` —
    // exactly the failure mode that hit two "full rail" goldens in Phase 2d.

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun reader_vertical_paper_1x() = captureScreen("reader_vertical_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    /**
     * Spec risk 8.1, answered rather than deferred: the translation bubble pops over a pane that
     * is only about six lines tall on a phone. No fixture anywhere had ever set `wordSelection`,
     * so the bubble had never been captured in ANY golden, in either orientation — this is the
     * first. What it must show: the bubble sitting above the bottom edge without covering the
     * divider, and the unselected translation dimmed.
     */
    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun reader_vertical_selection_paper_1x() = captureScreen("reader_vertical_selection_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        ReaderContent(
            state = readerFullyTranslated.copy(
                wordSelection = WordSelection(
                    word = "Ishmael",
                    chapterIndex = 0,
                    paragraphIndex = 0,
                    startChar = 8,
                    endChar = 15,
                    translation = "Измаил",
                ),
            ),
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun reader_vertical_night_1x() = captureScreen("reader_vertical_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    /** Non-default vertical split (35/65): pane weights and the handle must follow the ratio. */
    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun reader_vertical_split035_paper_1x() = captureScreen("reader_vertical_split035_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        ReaderContent(
            state = readerFullyTranslated.copy(verticalSplitRatio = 0.35f),
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun reader_vertical_rtl_1x() = captureScreen("reader_vertical_rtl_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f, rtl = true) {
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    /**
     * Acceptance criterion: a tablet in portrait is 800dp wide — ~47 characters per column — and
     * must KEEP the side-by-side layout. This golden is the only thing standing between the
     * width-based trigger and someone "simplifying" it to an orientation check.
     */
    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun reader_tablet_portrait_paper_1x() = captureScreen("reader_tablet_portrait_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    // ── Vertical split slider + portrait hint (Task 7) ─────────────────────────

    /**
     * `portraitHintDismissed = false` (unlike every other fixture above) so `ReaderContent`'s own
     * trigger — `vertical && !state.portraitHintDismissed && !hintShownThisEntry` — fires for
     * real, exercising the actual wiring rather than rendering `PortraitHintDialog` standalone.
     */
    // changeThreshold = 0.05f (default 0.01f): confirmed by direct pixel diff — not a functional
    // difference — that re-recording this golden shows ~2.8% of pixels changed, confined entirely to
    // the two-line dialog title, with the two captures visually indistinguishable. A big title
    // occupies a much larger share of this small dialog-only crop than of a full-screen golden, so
    // the same AA-jitter (documented above on `roborazziOptions`) that the suite's default 1%
    // threshold absorbs elsewhere isn't generous enough here. See ScreenshotTest.kt.
    @Test
    @Config(qualifiers = PHONE_NARROW)
    fun reader_vertical_hint_paper_1x() = captureScreen("reader_vertical_hint_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f, changeThreshold = 0.05f) {
        ReaderContent(
            state = readerFullyTranslated.copy(portraitHintDismissed = false),
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
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            onSetNavigationSide = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onReadingPositionChanged = {},
            onMarkFinished = {},
            onToggleBookmark = {},
            onRemoveBookmark = { _, _ -> },
            onJumpToBookmark = { _, _ -> },
            onConsumePendingJump = {},
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
            onDismissPortraitHint = {},
        )
    }

    /**
     * `DisplaySettingsDialog` captured directly (not via `ReaderContent`'s internal
     * `showDisplaySettings` state) with `vertical = true` — the split slider must read "Vertical
     * split" and drive `state.verticalSplitRatio`, not "Split position".
     *
     * [PHONE_TALL], not [PHONE_PORTRAIT]: the dialog's own scrollable region is capped to 90% of
     * the device height, and at PHONE_PORTRAIT's 891dp that region runs out before reaching the
     * split slider near the bottom of the dialog's content — the golden would render without the
     * one thing it exists to prove. PHONE_TALL is still compact-width, so `vertical` stays true.
     */
    @Test
    @Config(qualifiers = PHONE_TALL)
    fun reader_vertical_display_settings_paper_1x() = captureScreen("reader_vertical_display_settings_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        DisplaySettingsDialog(
            state = readerFullyTranslated,
            onSetReaderTheme = {},
            onAdjustTextSize = {},
            onAdjustLineHeight = {},
            onSetReadingFont = {},
            onSetLetterSpacing = {},
            onSetTextIndent = {},
            onSetParagraphSpacing = {},
            onSetJustifyText = {},
            onSetSplitRatio = {},
            vertical = true,
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            wordHighlightEnabled = true,
            onToggleWordHighlight = {},
            onSetOrientationLock = {},
            onDismiss = {},
        )
    }

    /**
     * Deliberately at TABLET *width* — the audit in Phase 2c found the project had zero dialog
     * goldens, so the landscape/wide slider had never been captured either, and this is the cheap
     * moment to fix that for the surface being touched. `vertical = false` here must render
     * "Split position" driving `state.splitRatio`.
     *
     * [TABLET_TALL], not the bare class-level [TABLET]: same reasoning as
     * `reader_vertical_display_settings_paper_1x` above — at TABLET's 800dp height the dialog's
     * capped scroll region runs out before the split slider, same width (so `vertical` stays
     * false), just enough height that nothing needed for the check is clipped.
     */
    @Test
    @Config(qualifiers = TABLET_TALL)
    fun reader_display_settings_paper_1x() = captureScreen("reader_display_settings_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        DisplaySettingsDialog(
            state = readerFullyTranslated,
            onSetReaderTheme = {},
            onAdjustTextSize = {},
            onAdjustLineHeight = {},
            onSetReadingFont = {},
            onSetLetterSpacing = {},
            onSetTextIndent = {},
            onSetParagraphSpacing = {},
            onSetJustifyText = {},
            onSetSplitRatio = {},
            vertical = false,
            onSetVerticalSplitRatio = {},
            onToggleTranslation = {},
            onToggleIllustrations = {},
            wordHighlightEnabled = true,
            onToggleWordHighlight = {},
            onSetOrientationLock = {},
            onDismiss = {},
        )
    }
}
