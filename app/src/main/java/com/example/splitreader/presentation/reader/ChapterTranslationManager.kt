package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A snapshot of translated paragraphs plus the foreground translation banner state. */
data class TranslationUpdate(
    val translations: Map<Int, List<String>>,
    val state: TranslationState,
)

/**
 * Owns the reader's translation orchestration. Translation follows the *viewport*: the reader reports
 * the paragraph span it can actually see (which may cross chapter boundaries), and a single serialized
 * worker translates that span first, then a bounded look-ahead/look-behind (see [TranslationPlanner]).
 *
 * The worker is re-targeted rather than cancelled when the viewport moves: an in-flight paragraph
 * always finishes (its result is cached), and after each segment the worker re-reads the latest window
 * and recomputes the plan. So flinging through short chapters never strands half-translated chapters,
 * and scrolling up immediately picks up the newly-visible (earlier) paragraphs — the same pipeline for
 * ML Kit and paid engines, with paid engines simply gated off while the pane is hidden.
 */
class ChapterTranslationManager(
    private val scope: CoroutineScope,
    private val translateTextUseCase: TranslateTextUseCase,
    private val isMlKit: () -> Boolean,
    private val isTranslationVisible: () -> Boolean,
) {
    val updates: SharedFlow<TranslationUpdate> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val sink get() = updates as MutableSharedFlow<TranslationUpdate>

    private val mutex = Mutex()

    private var book: Book? = null
    private var source: Language = Language.ENGLISH
    private var target: Language = Language.ENGLISH

    /** Authoritative per-chapter translated text; index-aligned to each chapter's paragraphs. */
    private val results = mutableMapOf<Int, MutableList<String>>()
    /** Immutable snapshot mirror handed to the UI (rebuilt per changed chapter). */
    private var snapshot: Map<Int, List<String>> = emptyMap()
    /**
     * Per-chapter set of paragraph indices already translated. Tracked explicitly (rather than via
     * empty text) so legitimately blank paragraphs count as done and the planner can prune them.
     */
    private val translatedIndex = mutableMapOf<Int, MutableSet<Int>>()

    private var currentState: TranslationState = TranslationState.Idle

    /** The latest viewport span the reader reported; the worker always plans against this. */
    private data class VisibleWindow(
        val startChapter: Int,
        val startPara: Int,
        val endChapter: Int,
        val endPara: Int,
    )
    private var latestWindow: VisibleWindow? = null

    /** Conflated wake-up signal: collapses a burst of scroll updates into a single re-plan. */
    private val windowSignal = Channel<Unit>(Channel.CONFLATED)
    private var workerJob: Job? = null
    /** One-off full-chapter fill (paid escape-hatch), kept off the viewport worker. */
    private var wholeChapterJob: Job? = null

    fun attach(book: Book, source: Language, target: Language) {
        this.book = book
        this.source = source
        this.target = target
    }

    fun setLanguages(source: Language, target: Language) {
        this.source = source
        this.target = target
    }

    /**
     * Report the visible paragraph span (start/end may be in different chapters). Re-targets the
     * worker to translate this window + look-ahead/behind, picking up where it left off.
     */
    fun onVisibleRange(startChapter: Int, startPara: Int, endChapter: Int, endPara: Int) {
        if (book == null) return
        latestWindow = VisibleWindow(startChapter, startPara, endChapter, endPara)
        ensureWorker()
        windowSignal.trySend(Unit)
    }

    /** Explicit request (paid escape-hatch) to finish translating a whole chapter. */
    fun translateWholeChapter(chapterIndex: Int) {
        val book = book ?: return
        val size = book.chapters.getOrNull(chapterIndex)?.paragraphs?.size ?: return
        if (size <= 0) return
        wholeChapterJob?.cancel()
        wholeChapterJob = scope.launch {
            runSegment(chapterIndex, 0, size - 1, foreground = false)
        }
    }

    /** Re-run a failed chapter from scratch at the reader's current window. */
    fun retry(chapterIndex: Int) {
        results.remove(chapterIndex)
        translatedIndex.remove(chapterIndex)
        snapshot = snapshot - chapterIndex
        emitState(TranslationState.Idle)
        ensureWorker()
        windowSignal.trySend(Unit)
    }

    /** Discard everything (e.g. target language changed) and emit an empty state. */
    fun reset() {
        cancelAll()
        results.clear()
        translatedIndex.clear()
        snapshot = emptyMap()
        latestWindow = null
        emitState(TranslationState.Idle)
    }

    fun cancelAll() {
        workerJob?.cancel()
        workerJob = null
        wholeChapterJob?.cancel()
        wholeChapterJob = null
    }

    private fun isTranslated(chapter: Int, para: Int): Boolean =
        translatedIndex[chapter]?.contains(para) == true

    private fun ensureWorker() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            for (signal in windowSignal) {
                processWindow()
            }
        }
    }

    /**
     * Translate the current window one segment at a time, re-reading [latestWindow] and recomputing
     * the plan after each segment so the worker chases the viewport without cancelling completed work.
     */
    private suspend fun processWindow() {
        val book = book ?: return
        val sizes = book.chapters.map { it.paragraphs.size }
        while (true) {
            val window = latestWindow ?: return
            // Don't burn paid quota translating text the reader has hidden; ML Kit (free) keeps going.
            if (!TranslationPlanner.shouldTranslate(isMlKit(), isTranslationVisible())) {
                emitState(TranslationState.Idle)
                return
            }
            val plan = TranslationPlanner.windowPlan(
                window.startChapter, window.startPara,
                window.endChapter, window.endPara,
                sizes, ::isTranslated,
            )
            val segment = plan.firstOrNull()
            if (segment == null) {
                emitState(TranslationState.Idle)
                return
            }
            // The visible window is done once we reach the silent look-ahead/behind: clear the banner.
            if (!segment.foreground &&
                currentState !is TranslationState.Idle &&
                currentState !is TranslationState.Error
            ) {
                emitState(TranslationState.Idle)
            }
            val ok = runSegment(segment.chapterIndex, segment.start, segment.endInclusive, segment.foreground)
            if (!ok) return // error already surfaced (foreground); wait for retry / next scroll
        }
    }

    /**
     * Translate one contiguous range, folding partials into [results]/[snapshot] and marking each
     * paragraph done in [translatedIndex]. Only foreground segments drive the progress banner.
     * Returns false if the range errored (foreground errors are surfaced; background errors are silent).
     */
    private suspend fun runSegment(
        chapterIndex: Int,
        start: Int,
        end: Int,
        foreground: Boolean,
    ): Boolean {
        val book = book ?: return false
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return false
        if (chapter.paragraphs.isEmpty()) return true
        val chapterResults = results.getOrPut(chapterIndex) { MutableList(chapter.paragraphs.size) { "" } }
        val total = (end - start + 1).coerceAtLeast(1)
        var done = 0
        var failed = false

        if (foreground) emitState(TranslationState.Translating(0))

        translateTextUseCase(
            chapter.paragraphs,
            source,
            target,
            startIndex = start,
            endIndex = end,
        ).collect { state ->
            when (state) {
                is TranslationState.Partial -> mutex.withLock {
                    if (state.index in chapterResults.indices) chapterResults[state.index] = state.text
                    translatedIndex.getOrPut(chapterIndex) { mutableSetOf() }.add(state.index)
                    done++
                    snapshot = snapshot + (chapterIndex to chapterResults.toList())
                    if (foreground) {
                        currentState = TranslationState.Translating(TranslationPlanner.progressPercent(done, total))
                    }
                    sink.tryEmit(TranslationUpdate(snapshot, currentState))
                }
                is TranslationState.DownloadingModel -> if (foreground) emitState(TranslationState.DownloadingModel)
                is TranslationState.Error -> {
                    if (foreground) emitState(state)
                    failed = true
                }
                else -> Unit
            }
        }
        return !failed
    }

    private fun emitState(state: TranslationState) {
        currentState = state
        sink.tryEmit(TranslationUpdate(snapshot, state))
    }
}
