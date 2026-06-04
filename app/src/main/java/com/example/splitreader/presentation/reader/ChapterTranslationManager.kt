package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
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
 * Owns the reader's translation orchestration: one high-priority foreground window anchored at
 * the visible paragraph, plus a provider-specific background fill — the same pipeline for ML Kit
 * and paid engines (see [TranslationPlanner]). Switching chapters or jumping cancels stale work so
 * the engine never translates the previous chapter ahead of the one the reader actually opened, and
 * only the foreground window drives the progress banner (background fill is silent and monotonic).
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
    /** Immutable snapshot mirror handed to the UI (rebuilt per changed chapter, like the old state). */
    private var snapshot: Map<Int, List<String>> = emptyMap()
    /** The furthest contiguous paragraph index translated per chapter. */
    private val prefetchedEdge = mutableMapOf<Int, Int>()

    private var currentState: TranslationState = TranslationState.Idle
    private var activeChapter = -1
    private var activeAnchor = 0

    private var foregroundJob: Job? = null
    private var backgroundJob: Job? = null
    private var prefetchJob: Job? = null

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
     * Focus a chapter: translate its visible window first, then kick off the background fill.
     * No-op when the chapter is already the active one (scrolling within it is handled by [onScroll]).
     */
    fun focusChapter(chapterIndex: Int, anchor: Int) {
        val book = book ?: return
        if (chapterIndex < 0 || chapterIndex >= book.chapters.size) return
        // Don't burn paid quota translating text the reader has hidden; ML Kit (free) keeps going.
        if (!TranslationPlanner.shouldTranslate(isMlKit(), isTranslationVisible())) {
            activeAnchor = anchor
            return
        }
        if (chapterIndex == activeChapter) return
        activeChapter = chapterIndex
        activeAnchor = anchor

        foregroundJob?.cancel()
        backgroundJob?.cancel()
        prefetchJob?.cancel()

        val size = book.chapters[chapterIndex].paragraphs.size
        // Already fully translated (e.g. ML Kit pre-loaded it): skip the window, just preload ahead.
        if ((prefetchedEdge[chapterIndex] ?: -1) >= size - 1 && size > 0) {
            emitState(TranslationState.Idle)
            startBackground(chapterIndex, size - 1, anchor)
            return
        }

        foregroundJob = scope.launch {
            val fg = TranslationPlanner.foregroundRange(anchor, size)
            if (fg.isEmpty()) {
                emitState(TranslationState.Idle)
                return@launch
            }
            val ok = runSegment(chapterIndex, fg.first, fg.last, foreground = true)
            if (!ok) return@launch
            setEdge(chapterIndex, fg.last)
            emitState(TranslationState.Idle)
            startBackground(chapterIndex, fg.last, anchor)
        }
    }

    /** Drive translation from a scroll update: re-focus on chapter change, else prefetch ahead (paid). */
    fun onScroll(chapterIndex: Int, anchor: Int) {
        if (!TranslationPlanner.shouldTranslate(isMlKit(), isTranslationVisible())) {
            activeAnchor = anchor
            return
        }
        if (chapterIndex != activeChapter) {
            focusChapter(chapterIndex, anchor)
            return
        }
        activeAnchor = anchor
        maybePrefetch(chapterIndex, anchor)
    }

    /** Explicit request (paid escape-hatch) to finish translating the rest of a chapter. */
    fun translateWholeChapter(chapterIndex: Int) {
        val book = book ?: return
        val size = book.chapters.getOrNull(chapterIndex)?.paragraphs?.size ?: return
        val edge = prefetchedEdge[chapterIndex] ?: -1
        if (edge >= size - 1) return
        prefetchJob?.cancel()
        backgroundJob?.cancel()
        backgroundJob = scope.launch {
            if (runSegment(chapterIndex, edge + 1, size - 1, foreground = false)) {
                setEdge(chapterIndex, size - 1)
            }
        }
    }

    /** Re-run a failed chapter from scratch at the reader's current position. */
    fun retry(chapterIndex: Int) {
        foregroundJob?.cancel()
        backgroundJob?.cancel()
        prefetchJob?.cancel()
        results.remove(chapterIndex)
        prefetchedEdge.remove(chapterIndex)
        snapshot = snapshot - chapterIndex
        activeChapter = -1
        emitState(TranslationState.Idle)
        focusChapter(chapterIndex, activeAnchor)
    }

    /** Discard everything (e.g. target language changed) and emit an empty state. */
    fun reset() {
        cancelAll()
        results.clear()
        prefetchedEdge.clear()
        snapshot = emptyMap()
        activeChapter = -1
        emitState(TranslationState.Idle)
    }

    fun cancelAll() {
        foregroundJob?.cancel()
        backgroundJob?.cancel()
        prefetchJob?.cancel()
    }

    private fun startBackground(chapterIndex: Int, foregroundEnd: Int, anchor: Int) {
        val book = book ?: return
        val sizes = book.chapters.map { it.paragraphs.size }
        val plan = TranslationPlanner.backgroundPlan(isMlKit(), chapterIndex, foregroundEnd, anchor, sizes)
        if (plan.isEmpty()) return
        backgroundJob = scope.launch {
            for (segment in plan) {
                val ok = runSegment(segment.chapterIndex, segment.start, segment.endInclusive, foreground = false)
                if (!ok) break
                if (segment.endInclusive > (prefetchedEdge[segment.chapterIndex] ?: -1)) {
                    setEdge(segment.chapterIndex, segment.endInclusive)
                }
            }
        }
    }

    private fun maybePrefetch(chapterIndex: Int, anchor: Int) {
        if (isMlKit()) return // ML Kit background already fills the whole chapter
        val book = book ?: return
        val size = book.chapters.getOrNull(chapterIndex)?.paragraphs?.size ?: return
        val edge = prefetchedEdge[chapterIndex] ?: return
        if (!TranslationPlanner.needsPrefetch(edge, anchor, size)) return
        if (prefetchJob?.isActive == true) return
        val range = TranslationPlanner.prefetchRange(edge, anchor, size) ?: return
        prefetchJob = scope.launch {
            if (runSegment(chapterIndex, range.first, range.last, foreground = false)) {
                setEdge(chapterIndex, range.last)
            }
        }
    }

    /**
     * Translate one contiguous range, folding partials into [results]/[snapshot]. Only foreground
     * segments drive the progress banner; background segments update text silently. Returns false
     * if the range errored (foreground errors are surfaced; background errors stay silent).
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

    private fun setEdge(chapterIndex: Int, edge: Int) {
        val existing = prefetchedEdge[chapterIndex] ?: -1
        if (edge > existing) prefetchedEdge[chapterIndex] = edge
    }

    private fun emitState(state: TranslationState) {
        currentState = state
        sink.tryEmit(TranslationUpdate(snapshot, state))
    }
}
