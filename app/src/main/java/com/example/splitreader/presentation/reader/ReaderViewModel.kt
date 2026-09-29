package com.example.splitreader.presentation.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.repository.TranslatorKeyStore
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.repository.SpeechSynthesizer
import com.example.splitreader.domain.repository.TranslatorEndpointStore
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.model.coercedTo
import com.example.splitreader.domain.model.isAtStart
import com.example.splitreader.domain.model.paragraphOrdinal
import com.example.splitreader.domain.parser.SynopsisExtractor
import com.example.splitreader.domain.repository.BookmarkRepository
import com.example.splitreader.domain.repository.TranslationUsageStats
import com.example.splitreader.domain.translator.TranslationProviderApi
import com.example.splitreader.domain.usecase.EndReadingSessionUseCase
import com.example.splitreader.domain.usecase.MigrateLegacyReadingPositionsUseCase
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.example.splitreader.domain.usecase.SaveWordResult
import com.example.splitreader.domain.usecase.SaveWordUseCase
import com.example.splitreader.domain.usecase.ToggleBookmarkUseCase
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.ReadingFont
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

// TODO(architecture): this ViewModel has several responsibilities (book loading, translation +
//  prefetch orchestration, word selection/translation, saved-word handling, TTS, reading-session
//  tracking, settings persistence). Consider extracting the chapter-translation/prefetch engine and
//  the word-selection logic into separate collaborators to reduce its size and surface area.
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val translateTextUseCase: TranslateTextUseCase,
    private val parseBookUseCase: ParseBookUseCase,
    private val saveWordUseCase: SaveWordUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val endReadingSessionUseCase: EndReadingSessionUseCase,
    private val migrateLegacyReadingPositions: MigrateLegacyReadingPositionsUseCase,
    private val crashReporter: CrashReporter,
    private val progressManager: ReadingPreferences,
    private val languageDetector: LanguageDetector,
    private val apiKeyManager: TranslatorKeyStore,
    private val translatorEndpoints: TranslatorEndpointStore,
    private val usageTracker: TranslationUsageStats,
    private val textToSpeechManager: SpeechSynthesizer,
    private val translationProviders: Map<TranslationProvider, @JvmSuppressWildcards TranslationProviderApi>,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private fun buildTranslatorConfig(current: TranslationProvider): TranslatorConfigState =
        buildTranslatorConfigState(translationProviders, translatorEndpoints, usageTracker, current)

    /**
     * Rebuilds [TranslatorConfigState] off the main thread — [buildTranslatorConfig] reads
     * [TranslatorKeyStore] (Android Keystore IPC), which must not run at construction/on the caller's
     * (often main) thread.
     */
    private fun refreshTranslatorConfig(provider: TranslationProvider) {
        viewModelScope.launch(Dispatchers.Default) {
            val cfg = buildTranslatorConfig(provider)
            _state.update { if (it.translatorProvider == provider) it.copy(translatorConfig = cfg) else it }
        }
    }

    private data class InternalState(
        val book: Book? = null,
        val position: ReadingPosition = ReadingPosition.START,
        val sourceLanguage: Language = Language.ENGLISH,
        val targetLanguage: Language = Language.ENGLISH,
        val translationState: TranslationState = TranslationState.Idle,
        val chapterTranslations: Map<Int, List<String>> = emptyMap(),
        val pendingJump: ReadingPosition? = null,
        val textSize: Float = ReadingDefaults.TEXT_SIZE,
        val lineHeightMultiplier: Float = ReadingDefaults.LINE_HEIGHT,
        val readingFont: ReadingFont = ReadingFont.SERIF,
        val letterSpacing: Float = ReadingDefaults.LETTER_SPACING,
        val textIndent: Float = ReadingDefaults.TEXT_INDENT,
        val paragraphSpacing: Float = ReadingDefaults.PARAGRAPH_SPACING,
        val justifyText: Boolean = ReadingDefaults.JUSTIFY_TEXT,
        val splitRatio: Float = ReadingDefaults.SPLIT_RATIO,
        val verticalSplitRatio: Float = ReadingDefaults.VERTICAL_SPLIT_RATIO,
        val portraitHintDismissed: Boolean = ReadingDefaults.PORTRAIT_HINT_DISMISSED,
        val showTranslation: Boolean = ReadingDefaults.SHOW_TRANSLATION,
        val showIllustrations: Boolean = ReadingDefaults.SHOW_ILLUSTRATIONS,
        val readerTheme: ReaderThemeKey = ReaderThemeKey.PAPER,
        val navigationSide: NavigationSide = NavigationSide.RIGHT,
        val horizontalMargin: Float = ReadingDefaults.HORIZONTAL_MARGIN,
        val orientationLock: OrientationLock = OrientationLock.AUTO,
        val isLoading: Boolean = false,
        val error: String? = null,
        val bookmarks: List<Bookmark> = emptyList(),
        val wordSelection: WordSelection? = null,
        val translatorProvider: TranslationProvider = TranslationProvider.MLKIT,
        val translatorConfig: TranslatorConfigState =
            TranslatorConfigState(current = TranslationProvider.MLKIT, configs = emptyMap()),
    )

    private val _state = MutableStateFlow(
        InternalState(
            targetLanguage = progressManager.getTargetLanguage(),
            navigationSide = if (progressManager.isNavigationLeft()) NavigationSide.LEFT else NavigationSide.RIGHT,
            readerTheme = when (progressManager.getReaderThemeName()) {
                "DEFAULT" -> ReaderThemeKey.PAPER // migrate old persisted value
                else -> ReaderThemeKey.entries.find { it.name == progressManager.getReaderThemeName() }
                    ?: ReaderThemeKey.PAPER
            },
            lineHeightMultiplier = progressManager.getLineHeightMultiplier(),
            textSize = progressManager.getTextSize(),
            readingFont = ReadingFont.entries.find { it.name == progressManager.getReadingFontName() }
                ?: ReadingFont.SERIF,
            letterSpacing = progressManager.getLetterSpacing(),
            textIndent = progressManager.getTextIndent(),
            paragraphSpacing = progressManager.getParagraphSpacing(),
            justifyText = progressManager.getJustifyText(),
            splitRatio = progressManager.getSplitRatio(),
            verticalSplitRatio = progressManager.getVerticalSplitRatio(),
            portraitHintDismissed = progressManager.getPortraitHintDismissed(),
            showTranslation = progressManager.getShowTranslation(),
            showIllustrations = progressManager.getShowIllustrations(),
            horizontalMargin = progressManager.getHorizontalMargin(),
            orientationLock = progressManager.getOrientationLock(),
            translatorProvider = progressManager.getTranslatorProvider(),
            translatorConfig = TranslatorConfigState(current = progressManager.getTranslatorProvider(), configs = emptyMap()),
        )
    )

    // One-shot events for transient UI feedback (e.g. Toast after saving a word)
    private val _wordSaveEvent = Channel<SaveWordResult>(Channel.BUFFERED)
    val wordSaveEvent = _wordSaveEvent.receiveAsFlow()

    // Guards concurrent state mutations inside the translation coroutine
    private val stateMutex = Mutex()

    val uiState = _state
        .map { s ->
            when {
                s.error != null -> ReaderUiState.Error(s.error)
                s.book == null -> ReaderUiState.Loading
                else -> ReaderUiState.Success(
                    book = s.book,
                    position = s.position,
                    sourceLanguage = s.sourceLanguage,
                    targetLanguage = s.targetLanguage,
                    translationState = s.translationState,
                    chapterTranslations = s.chapterTranslations,
                    pendingJump = s.pendingJump,
                    textSize = s.textSize,
                    lineHeightMultiplier = s.lineHeightMultiplier,
                    readingFont = s.readingFont,
                    letterSpacing = s.letterSpacing,
                    textIndent = s.textIndent,
                    paragraphSpacing = s.paragraphSpacing,
                    justifyText = s.justifyText,
                    splitRatio = s.splitRatio,
                    verticalSplitRatio = s.verticalSplitRatio,
                    portraitHintDismissed = s.portraitHintDismissed,
                    showTranslation = s.showTranslation,
                    showIllustrations = s.showIllustrations,
                    readerTheme = s.readerTheme,
                    navigationSide = s.navigationSide,
                    horizontalMargin = s.horizontalMargin,
                    orientationLock = s.orientationLock,
                    bookmarks = s.bookmarks,
                    isCurrentPositionBookmarked = s.bookmarks.any {
                        it.chapterIndex == s.position.chapter && it.paragraphIndex == s.position.paragraph
                    },
                    wordSelection = s.wordSelection,
                    translatorProvider = s.translatorProvider,
                    translatorConfig = s.translatorConfig,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderUiState.Loading,
        )

    private val translationManager = ChapterTranslationManager(
        scope = viewModelScope,
        translateTextUseCase = translateTextUseCase,
        isFreeEngine = { TranslationPlanner.isFreeEngine(progressManager.getTranslatorProvider()) },
        isTranslationVisible = { progressManager.getShowTranslation() },
    )
    private var selectionTranslateJob: Job? = null

    init {
        // Populate the real translator config (Keystore read) off the main thread; the field
        // initializer above only seeds a cheap prefs-only placeholder.
        refreshTranslatorConfig(progressManager.getTranslatorProvider())
        // Fold the translation engine's snapshots (translated text + foreground banner state) into UI state.
        viewModelScope.launch {
            translationManager.updates.collect { update ->
                stateMutex.withLock {
                    _state.update {
                        it.copy(
                            chapterTranslations = update.translations,
                            translationState = update.state,
                        )
                    }
                }
            }
        }
    }

    // Timestamp of the current foreground reading stint; 0 means no active session.
    private var sessionStartedAt = 0L

    // Book-wide paragraph ordinal where the current session started; paragraphsRead is measured from it.
    private var sessionStartOrdinal = 0

    fun loadBook(uri: Uri) {
        viewModelScope.launch {
            parseBookUseCase(uri).collect { result ->
                when (result) {
                    is ParseResult.Loading -> _state.update { it.copy(isLoading = true) }
                    is ParseResult.Success -> handleBookLoaded(result.book)
                    is ParseResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                    is ParseResult.Idle -> Unit
                }
            }
        }
    }

    private suspend fun handleBookLoaded(book: Book) {
        // The migration's prefs write is a synchronous commit(); keep it off the main thread.
        val showIllustrations = _state.value.showIllustrations
        try {
            withContext(Dispatchers.IO) { migrateLegacyReadingPositions(book, showIllustrations) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't fail the book open. The flag is written before bookmarks, so a failure there leaves
            // them in legacy coordinates rather than retrying (see MigrateLegacyReadingPositionsUseCase).
            // A failure BEFORE the flag is written leaves the book unmigrated, so it retries on the next open
            // (by design; a persistent pre-flag failure would eventually treat bookmarks added in new
            // coordinates as legacy).
            crashReporter.recordNonFatal(e, "Legacy reading-position migration failed")
        }
        val saved = progressManager.getReadingPosition(book.filePath).coercedTo(book)
        val detectedLang = languageDetector.detectLanguage(buildLanguageSample(book))
        val savedTarget = _state.value.targetLanguage
        val targetLang = if (detectedLang == savedTarget) {
            if (detectedLang == Language.ENGLISH) Language.RUSSIAN else Language.ENGLISH
        } else {
            savedTarget
        }
        progressManager.saveTargetLanguage(targetLang)

        _state.update {
            it.copy(
                book = book,
                isLoading = false,
                position = saved,
                sourceLanguage = detectedLang,
                targetLanguage = targetLang,
                pendingJump = saved.takeUnless { p -> book.isAtStart(p) },
            )
        }
        translationManager.attach(book, detectedLang, targetLang)
        // Seed the window at the restored paragraph so it translates before the first visible-range report.
        translationManager.onVisibleRange(saved.chapter, saved.paragraph, saved.chapter, saved.paragraph)
        // Begin tracking reading time now that the book is on screen
        resumeSession()
        observeBookmarks(book.filePath)
    }

    private fun observeBookmarks(bookUri: String) {
        viewModelScope.launch {
            bookmarkRepository.observeForBook(bookUri).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }
    }

    /** Start a reading-time session if a book is loaded and none is already running. */
    fun resumeSession() {
        val book = _state.value.book
        if (book != null && sessionStartedAt == 0L) {
            sessionStartedAt = System.currentTimeMillis()
            sessionStartOrdinal = book.paragraphOrdinal(_state.value.position)
        }
    }

    /** End the current reading session (if any) and persist it via [EndReadingSessionUseCase]. */
    fun pauseSession() {
        val startedAt = sessionStartedAt
        val book = _state.value.book
        if (startedAt == 0L || book == null) return
        sessionStartedAt = 0L
        val sourceLang = _state.value.sourceLanguage.code
        // How far the reader advanced this session, in paragraphs (never negative: re-reading counts 0).
        val paragraphsRead = (book.paragraphOrdinal(_state.value.position) - sessionStartOrdinal).coerceAtLeast(0)
        viewModelScope.launch {
            endReadingSessionUseCase(
                startedAt = startedAt,
                bookUri = book.filePath,
                bookTitle = book.title,
                sourceLang = sourceLang,
                paragraphsRead = paragraphsRead,
            )
        }
    }

    fun selectChapter(index: Int) {
        val book = _state.value.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        _state.update { it.copy(position = ReadingPosition(index, 0, 0)) }
        // A jump lands at the top of the chapter; seed the window there and the reader's visible-range
        // report will widen it once the chapter is laid out.
        translationManager.onVisibleRange(index, 0, index, 0)
    }

    fun setTargetLanguage(lang: Language) {
        progressManager.saveTargetLanguage(lang)
        translationManager.reset()
        translationManager.setLanguages(_state.value.sourceLanguage, lang)
        _state.update { it.copy(targetLanguage = lang, chapterTranslations = emptyMap()) }
        val p = _state.value.position
        translationManager.onVisibleRange(p.chapter, p.paragraph, p.chapter, p.paragraph)
    }

    /** The reader's top paragraph changed (reported through BookItemIndex.positionAt). */
    fun onReadingPositionChanged(position: ReadingPosition) {
        val book = _state.value.book ?: return
        _state.update { it.copy(position = position) }
        progressManager.saveReadingPosition(book.filePath, position)
        // "Continue reading" excerpt for the Library hero; nothing has been read at the very start.
        if (!book.isAtStart(position)) {
            val paragraph = book.chapters.getOrNull(position.chapter)?.paragraphs?.getOrNull(position.paragraph)
            SynopsisExtractor.normalize(paragraph)?.let { progressManager.saveExcerpt(book.filePath, it) }
        }
    }

    /** Toggles a bookmark at the user's current reading position (current chapter + top paragraph). */
    fun toggleBookmarkAtCurrentPosition() {
        val book = _state.value.book ?: return
        val p = _state.value.position
        viewModelScope.launch {
            toggleBookmarkUseCase(book.filePath, p.chapter, p.paragraph)
        }
    }

    /** Removes a specific bookmark (used by the bookmarks list). */
    fun removeBookmarkAt(chapterIndex: Int, paragraphIndex: Int) {
        val book = _state.value.book ?: return
        viewModelScope.launch {
            toggleBookmarkUseCase(book.filePath, chapterIndex, paragraphIndex)
        }
    }

    /** Jumps to a bookmarked paragraph (always its start) through the single pending-jump path. */
    fun jumpToBookmark(chapterIndex: Int, paragraphIndex: Int) {
        val book = _state.value.book ?: return
        if (chapterIndex !in book.chapters.indices) return
        val target = ReadingPosition(chapterIndex, paragraphIndex, 0).coercedTo(book)
        _state.update { it.copy(position = target, pendingJump = target) }
        translationManager.onVisibleRange(target.chapter, target.paragraph, target.chapter, target.paragraph)
    }

    /** Called by the reader when the user scrolls to the end of the last chapter. */
    fun markFinished() {
        val book = _state.value.book ?: return
        progressManager.markFinished(book.filePath)
    }

    fun consumePendingJump() {
        _state.update { it.copy(pendingJump = null) }
    }

    fun adjustTextSize(delta: Float) {
        val newSize = (_state.value.textSize + delta).coerceIn(ReadingDefaults.TEXT_SIZE_RANGE)
        progressManager.saveTextSize(newSize)
        _state.update { it.copy(textSize = newSize) }
    }

    fun setReadingFont(font: ReadingFont) {
        progressManager.saveReadingFont(font.name)
        _state.update { it.copy(readingFont = font) }
    }

    fun setLetterSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(ReadingDefaults.LETTER_SPACING_RANGE)
        progressManager.saveLetterSpacing(clamped)
        _state.update { it.copy(letterSpacing = clamped) }
    }

    fun setTextIndent(indent: Float) {
        val clamped = indent.coerceIn(ReadingDefaults.TEXT_INDENT_RANGE)
        progressManager.saveTextIndent(clamped)
        _state.update { it.copy(textIndent = clamped) }
    }

    fun setParagraphSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(ReadingDefaults.PARAGRAPH_SPACING_RANGE)
        progressManager.saveParagraphSpacing(clamped)
        _state.update { it.copy(paragraphSpacing = clamped) }
    }

    fun setJustifyText(justify: Boolean) {
        progressManager.saveJustifyText(justify)
        _state.update { it.copy(justifyText = justify) }
    }

    fun setNavigationSide(side: NavigationSide) {
        progressManager.saveNavigationSideLeft(side == NavigationSide.LEFT)
        _state.update { it.copy(navigationSide = side) }
    }

    fun setReaderTheme(theme: ReaderThemeKey) {
        progressManager.saveReaderTheme(theme.name)
        _state.update { it.copy(readerTheme = theme) }
    }

    fun adjustLineHeight(delta: Float) {
        val newMultiplier = (_state.value.lineHeightMultiplier + delta).coerceIn(ReadingDefaults.LINE_HEIGHT_RANGE)
        progressManager.saveLineHeightMultiplier(newMultiplier)
        _state.update { it.copy(lineHeightMultiplier = newMultiplier) }
    }

    fun setSplitRatio(ratio: Float) {
        val clamped = ratio.coerceIn(ReadingDefaults.SPLIT_RATIO_RANGE)
        progressManager.saveSplitRatio(clamped)
        _state.update { it.copy(splitRatio = clamped) }
    }

    fun setVerticalSplitRatio(ratio: Float) {
        val clamped = ratio.coerceIn(ReadingDefaults.VERTICAL_SPLIT_RATIO_RANGE)
        progressManager.saveVerticalSplitRatio(clamped)
        _state.update { it.copy(verticalSplitRatio = clamped) }
    }

    /** [dontShowAgain] persists; otherwise the hint simply does not reappear this entry. */
    fun dismissPortraitHint(dontShowAgain: Boolean) {
        if (dontShowAgain) progressManager.savePortraitHintDismissed(true)
        _state.update { it.copy(portraitHintDismissed = it.portraitHintDismissed || dontShowAgain) }
    }

    fun toggleTranslation() {
        val newValue = !_state.value.showTranslation
        progressManager.saveShowTranslation(newValue)
        _state.update { it.copy(showTranslation = newValue) }
        // Re-enabling the pane: translate the current view, which a paid provider skipped while hidden.
        if (newValue) {
            val p = _state.value.position
            translationManager.onVisibleRange(p.chapter, p.paragraph, p.chapter, p.paragraph)
        }
    }

    fun toggleIllustrations() {
        val newValue = !_state.value.showIllustrations
        progressManager.saveShowIllustrations(newValue)
        _state.update { it.copy(showIllustrations = newValue) }
    }

    fun setHorizontalMargin(margin: Float) {
        val clamped = margin.coerceIn(ReadingDefaults.HORIZONTAL_MARGIN_RANGE)
        progressManager.saveHorizontalMargin(clamped)
        _state.update { it.copy(horizontalMargin = clamped) }
    }

    /**
     * The saved value is picked up reactively by [com.example.splitreader.presentation.AppThemeViewModel]
     * (which exposes [ReadingPreferences.orientationLock] directly) and applied to
     * `Activity.requestedOrientation` by `MainActivity` — no extra wiring needed here, same as a
     * write from Settings. Note: since the manifest declares no `configChanges`, an orientation
     * change recreates the Activity; the reader re-composes and restores scroll by item index
     * rather than a pixel-exact offset. That is the same path a physical rotation already takes.
     */
    fun setOrientationLock(lock: OrientationLock) {
        progressManager.saveOrientationLock(lock)
        _state.update { it.copy(orientationLock = lock) }
    }

    fun selectProvider(provider: TranslationProvider) {
        if (_state.value.translatorProvider == provider) return
        progressManager.setTranslatorProvider(provider)
        _state.update { it.copy(translatorProvider = provider) }
        refreshTranslatorConfig(provider)
        retranslateCurrentChapter()
    }

    fun configureProvider(provider: TranslationProvider, key: String?, secondary: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            if (key != null) apiKeyManager.setKey(provider, key)
            if (provider.secondaryLabel != null && secondary != null) translatorEndpoints.setSecondary(provider, secondary)
            val current = _state.value.translatorProvider
            val cfg = buildTranslatorConfig(current)
            _state.update { if (it.translatorProvider == current) it.copy(translatorConfig = cfg) else it }
            // retranslateCurrentChapter touches ChapterTranslationManager's non-thread-safe internal
            // state, which is only ever mutated from the Main thread — hop back for it.
            if (provider == _state.value.translatorProvider) withContext(Dispatchers.Main) { retranslateCurrentChapter() }
        }
    }

    fun clearProvider(provider: TranslationProvider) {
        viewModelScope.launch(Dispatchers.Default) {
            apiKeyManager.setKey(provider, null)
            val current = _state.value.translatorProvider
            val cfg = buildTranslatorConfig(current)
            _state.update { if (it.translatorProvider == current) it.copy(translatorConfig = cfg) else it }
            if (provider == _state.value.translatorProvider) withContext(Dispatchers.Main) { retranslateCurrentChapter() }
        }
    }

    fun refreshTranslationUsage() {
        refreshTranslatorConfig(_state.value.translatorProvider)
    }

    fun resetTranslationUsage(provider: TranslationProvider) {
        usageTracker.reset(provider)
        refreshTranslationUsage()
    }

    private fun retranslateCurrentChapter() {
        translationManager.reset()
        _state.update { it.copy(chapterTranslations = emptyMap(), translationState = TranslationState.Idle) }
        if (_state.value.book == null) return
        // Re-translate from the reader's current position; the manager re-plans the visible window.
        val p = _state.value.position
        translationManager.onVisibleRange(p.chapter, p.paragraph, p.chapter, p.paragraph)
    }

    fun selectWord(word: String, chapterIndex: Int, paragraphIndex: Int, startChar: Int, endChar: Int) {
        _state.update {
            it.copy(wordSelection = WordSelection(word, chapterIndex, paragraphIndex, startChar, endChar))
        }
        translateSelection(textToTranslate = word, expectedStart = startChar, expectedEnd = endChar, debounceMs = 0L)
    }

    fun clearWordSelection() {
        selectionTranslateJob?.cancel()
        selectionTranslateJob = null
        _state.update { it.copy(wordSelection = null) }
    }

    fun updateWordSelectionRange(startChar: Int, endChar: Int) {
        val current = _state.value.wordSelection ?: return
        val paragraphText = _state.value.book?.chapters
            ?.getOrNull(current.chapterIndex)?.paragraphs
            ?.getOrNull(current.paragraphIndex) ?: return

        val safeStart = startChar.coerceIn(0, paragraphText.length)
        val safeEnd = endChar.coerceIn(safeStart, paragraphText.length)
        if (safeEnd <= safeStart) return
        if (safeStart == current.startChar && safeEnd == current.endChar) return

        val substring = paragraphText.substring(safeStart, safeEnd).trim()
        if (substring.isEmpty()) return

        val newType = if (substring.any { it.isWhitespace() }) SelectionType.SENTENCE else SelectionType.WORD

        _state.update {
            it.copy(
                wordSelection = current.copy(
                    word = substring,
                    startChar = safeStart,
                    endChar = safeEnd,
                    translation = null,
                    selectionType = newType,
                ),
            )
        }
        translateSelection(textToTranslate = substring, expectedStart = safeStart, expectedEnd = safeEnd, debounceMs = 200L)
    }

    private fun translateSelection(
        textToTranslate: String,
        expectedStart: Int,
        expectedEnd: Int,
        debounceMs: Long,
    ) {
        selectionTranslateJob?.cancel()
        selectionTranslateJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            var translation = ""
            translateTextUseCase(
                paragraphs = listOf(textToTranslate),
                sourceLanguage = _state.value.sourceLanguage,
                targetLanguage = _state.value.targetLanguage,
                startIndex = 0,
                endIndex = 0,
            ).collect { state ->
                if (state is TranslationState.Partial) translation = state.text
            }
            _state.update { s ->
                val sel = s.wordSelection
                if (sel != null && sel.startChar == expectedStart && sel.endChar == expectedEnd) {
                    s.copy(wordSelection = sel.copy(translation = translation))
                } else s
            }
        }
    }

    fun saveWord(word: String, chapterIndex: Int, paragraphIndex: Int) {
        val s = _state.value
        val book = s.book ?: return
        val context = book.chapters
            .getOrNull(chapterIndex)?.paragraphs?.getOrElse(paragraphIndex) { "" }
            ?.take(120) ?: ""
        viewModelScope.launch {
            val result = saveWordUseCase(
                word = word,
                contextSnippet = context,
                sourceLang = s.sourceLanguage,
                targetLang = s.targetLanguage,
                bookUri = book.filePath,
                bookTitle = book.title,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
            )
            _wordSaveEvent.trySend(result)
        }
    }

    fun speak(text: String, langCode: String) = textToSpeechManager.speak(text, langCode)

    /**
     * The reader reports its visible span as true *paragraph* coordinates (the screen resolves
     * masthead/illustration items via `BookItemIndex` before calling this); the manager then
     * translates the visible window plus a bounded look-ahead/behind across chapter boundaries.
     */
    fun onVisibleRange(startChapter: Int, startParagraph: Int, endChapter: Int, endParagraph: Int) {
        translationManager.onVisibleRange(startChapter, startParagraph, endChapter, endParagraph)
    }

    /** Retries translation for the current chapter after a failure (clears it and re-runs). */
    fun retryTranslation() {
        translationManager.retry(_state.value.position.chapter)
    }

    /**
     * Paid escape-hatch: translate the remainder of the current chapter on demand. Paid providers
     * otherwise only translate the visible window plus a small look-ahead to conserve quota/tokens.
     */
    fun translateWholeChapter() {
        translationManager.translateWholeChapter(_state.value.position.chapter)
    }

    private fun buildLanguageSample(book: Book): String {
        val chapters = if (book.chapters.size > 1) book.chapters.drop(1) else book.chapters
        val sample = chapters
            .take(3)
            .flatMap { it.paragraphs.filter { p -> p.length > 30 }.take(5) }
            .joinToString(" ")
            .take(500)
        if (sample.isNotBlank()) return sample
        return book.chapters.flatMap { it.paragraphs }.filter { it.length > 30 }.take(10)
            .joinToString(" ").take(500)
    }
}
