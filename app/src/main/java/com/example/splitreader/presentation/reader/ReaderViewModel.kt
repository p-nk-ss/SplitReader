package com.example.splitreader.presentation.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val translateTextUseCase: TranslateTextUseCase,
    private val parseBookUseCase: ParseBookUseCase,
    private val progressManager: ReadingProgressManager,
    private val languageDetector: LanguageDetector,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private companion object {
        const val TRANSLATE_WINDOW = 25
    }

    private data class InternalState(
        val book: Book? = null,
        val currentChapterIndex: Int = 0,
        val sourceLanguage: Language = Language.ENGLISH,
        val targetLanguage: Language = Language.ENGLISH,
        val translationState: TranslationState = TranslationState.Idle,
        val translatedParagraphs: List<String> = emptyList(),
        val pendingScrollPosition: Int = -1,
        val pendingScrollOffset: Int = 0,
        val textSize: Float = 16f,
        val lineHeightMultiplier: Float = 1.5f,
        val splitRatio: Float = 0.5f,
        val showTranslation: Boolean = true,
        val readerTheme: ReaderTheme = ReaderTheme.DEFAULT,
        val navigationSide: NavigationSide = NavigationSide.RIGHT,
        val horizontalMargin: Float = 12f,
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(
        InternalState(
            targetLanguage = progressManager.getTargetLanguage(),
            navigationSide = if (progressManager.isNavigationLeft()) NavigationSide.LEFT else NavigationSide.RIGHT,
            readerTheme = ReaderTheme.entries.find { it.name == progressManager.getReaderThemeName() }
                ?: ReaderTheme.DEFAULT,
            lineHeightMultiplier = progressManager.getLineHeightMultiplier(),
            splitRatio = progressManager.getSplitRatio(),
            showTranslation = progressManager.getShowTranslation(),
            horizontalMargin = progressManager.getHorizontalMargin(),
        )
    )

    // Guards concurrent state mutations inside the translation coroutine
    private val stateMutex = Mutex()

    val uiState = _state
        .map { s ->
            when {
                s.error != null -> ReaderUiState.Error(s.error)
                s.book == null -> ReaderUiState.Loading
                else -> ReaderUiState.Success(
                    book = s.book,
                    currentChapterIndex = s.currentChapterIndex,
                    sourceLanguage = s.sourceLanguage,
                    targetLanguage = s.targetLanguage,
                    translationState = s.translationState,
                    translatedParagraphs = s.translatedParagraphs,
                    pendingScrollPosition = s.pendingScrollPosition,
                    pendingScrollOffset = s.pendingScrollOffset,
                    textSize = s.textSize,
                    lineHeightMultiplier = s.lineHeightMultiplier,
                    splitRatio = s.splitRatio,
                    showTranslation = s.showTranslation,
                    readerTheme = s.readerTheme,
                    navigationSide = s.navigationSide,
                    horizontalMargin = s.horizontalMargin,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderUiState.Loading,
        )

    private var translationJob: Job? = null
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0

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
        val lastChapterIndex = progressManager.getLastChapter(book.filePath)
            .coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
        val lastScroll = progressManager.getLastScrollPosition(book.filePath, lastChapterIndex)
        val lastOffset = progressManager.getLastScrollOffset(book.filePath, lastChapterIndex)
        val detectedLang = languageDetector.detectLanguage(buildLanguageSample(book))
        val savedTarget = _state.value.targetLanguage
        val targetLang = if (detectedLang == savedTarget) {
            if (detectedLang == Language.ENGLISH) Language.RUSSIAN else Language.ENGLISH
        } else {
            savedTarget
        }
        progressManager.saveTargetLanguage(targetLang)

        lastScrollPosition = lastScroll
        lastScrollOffset = lastOffset
        _state.update {
            it.copy(
                book = book,
                isLoading = false,
                currentChapterIndex = lastChapterIndex,
                sourceLanguage = detectedLang,
                targetLanguage = targetLang,
                pendingScrollPosition = if (lastScroll > 0) lastScroll else -1,
                pendingScrollOffset = if (lastScroll > 0) lastOffset else 0,
            )
        }
        translateChapter(lastChapterIndex)
    }

    fun selectChapter(index: Int) {
        val book = _state.value.book ?: return
        if (index < 0 || index >= book.chapters.size) return

        progressManager.saveProgress(book.filePath, _state.value.currentChapterIndex, lastScrollPosition, lastScrollOffset)
        lastScrollPosition = 0
        lastScrollOffset = 0
        translationJob?.cancel()
        _state.update {
            it.copy(
                currentChapterIndex = index,
                translatedParagraphs = emptyList(),
                translationState = TranslationState.Idle,
            )
        }
        translateChapter(index)
    }

    fun nextChapter() {
        val s = _state.value
        if (s.currentChapterIndex < (s.book?.chapters?.size ?: 0) - 1) {
            selectChapter(s.currentChapterIndex + 1)
        }
    }

    fun previousChapter() {
        val s = _state.value
        if (s.currentChapterIndex > 0) selectChapter(s.currentChapterIndex - 1)
    }

    fun previousChapterFromEnd() {
        val s = _state.value
        val book = s.book ?: return
        if (s.currentChapterIndex <= 0) return
        val prevIndex = s.currentChapterIndex - 1
        val prevChapter = book.chapters.getOrNull(prevIndex) ?: return
        val lastIndex = (prevChapter.paragraphs.size - 1).coerceAtLeast(0)

        progressManager.saveProgress(book.filePath, s.currentChapterIndex, lastScrollPosition, lastScrollOffset)
        lastScrollPosition = lastIndex
        lastScrollOffset = 0
        translationJob?.cancel()
        _state.update {
            it.copy(
                currentChapterIndex = prevIndex,
                translatedParagraphs = emptyList(),
                translationState = TranslationState.Idle,
                pendingScrollPosition = lastIndex,
                pendingScrollOffset = 0,
            )
        }
        translateChapter(prevIndex)
    }

    fun setTargetLanguage(lang: Language) {
        progressManager.saveTargetLanguage(lang)
        _state.update { it.copy(targetLanguage = lang) }
        translateChapter(_state.value.currentChapterIndex)
    }

    fun updateScrollPosition(position: Int, offset: Int = 0) {
        lastScrollPosition = position
        lastScrollOffset = offset
        val s = _state.value
        val book = s.book ?: return
        progressManager.saveProgress(book.filePath, s.currentChapterIndex, position, offset)
    }

    fun consumeScrollRestore() {
        _state.update { it.copy(pendingScrollPosition = -1, pendingScrollOffset = 0) }
    }

    fun adjustTextSize(delta: Float) {
        _state.update { it.copy(textSize = (it.textSize + delta).coerceIn(10f, 30f)) }
    }

    fun setNavigationSide(side: NavigationSide) {
        progressManager.saveNavigationSideLeft(side == NavigationSide.LEFT)
        _state.update { it.copy(navigationSide = side) }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        progressManager.saveReaderTheme(theme.name)
        _state.update { it.copy(readerTheme = theme) }
    }

    fun adjustLineHeight(delta: Float) {
        val newMultiplier = (_state.value.lineHeightMultiplier + delta).coerceIn(1.1f, 2.5f)
        progressManager.saveLineHeightMultiplier(newMultiplier)
        _state.update { it.copy(lineHeightMultiplier = newMultiplier) }
    }

    fun setSplitRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.3f, 0.7f)
        progressManager.saveSplitRatio(clamped)
        _state.update { it.copy(splitRatio = clamped) }
    }

    fun toggleTranslation() {
        val newValue = !_state.value.showTranslation
        progressManager.saveShowTranslation(newValue)
        _state.update { it.copy(showTranslation = newValue) }
    }

    fun setHorizontalMargin(margin: Float) {
        val clamped = margin.coerceIn(4f, 32f)
        progressManager.saveHorizontalMargin(clamped)
        _state.update { it.copy(horizontalMargin = clamped) }
    }

    private fun translateChapter(index: Int) {
        val book = _state.value.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        val chapter = book.chapters[index]
        val results = MutableList(chapter.paragraphs.size) { "" }

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            stateMutex.withLock {
                _state.update { it.copy(translatedParagraphs = results.toList()) }
            }

            val startIdx = lastScrollPosition.coerceIn(0, (chapter.paragraphs.size - 1).coerceAtLeast(0))
            val windowEnd = (startIdx + TRANSLATE_WINDOW - 1).coerceAtMost(chapter.paragraphs.size - 1)

            // Phase 1: translate the visible window immediately
            collectTranslations(chapter, results, startIdx, windowEnd)
            if (_state.value.translationState is TranslationState.Error) return@launch

            // Phase 2: translate from the end of the window to the chapter end
            if (windowEnd < chapter.paragraphs.size - 1) {
                collectTranslations(chapter, results, windowEnd + 1, chapter.paragraphs.size - 1)
                if (_state.value.translationState is TranslationState.Error) return@launch
            }

            // Phase 3: translate paragraphs before the starting position
            if (startIdx > 0) {
                collectTranslations(chapter, results, 0, startIdx - 1)
                if (_state.value.translationState is TranslationState.Error) return@launch
            }

            stateMutex.withLock {
                _state.update { it.copy(translationState = TranslationState.Idle) }
            }
        }
    }

    private suspend fun collectTranslations(
        chapter: Chapter,
        results: MutableList<String>,
        startIdx: Int,
        endIdx: Int,
    ) {
        translateTextUseCase(
            chapter.paragraphs,
            _state.value.sourceLanguage,
            _state.value.targetLanguage,
            startIndex = startIdx,
            endIndex = endIdx,
        ).collect { state ->
            when (state) {
                is TranslationState.Partial -> {
                    results[state.index] = state.text
                    val progress = ((state.index + 1) * 100) / chapter.paragraphs.size
                    stateMutex.withLock {
                        _state.update {
                            it.copy(
                                translatedParagraphs = results.toList(),
                                translationState = TranslationState.Translating(progress),
                            )
                        }
                    }
                }
                else -> stateMutex.withLock {
                    _state.update { it.copy(translationState = state) }
                }
            }
        }
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
