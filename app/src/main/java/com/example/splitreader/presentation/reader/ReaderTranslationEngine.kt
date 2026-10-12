package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A snapshot of translated paragraphs plus the foreground translation banner state.
 * [notice] is set when the chosen provider can't serve the current pair/device and a fallback
 * engine is translating instead; it is cleared by [ReaderTranslationEngine.discardTranslations]
 * (a target/provider change, [ReaderTranslationEngine.invalidate], or a new [ReaderTranslationEngine.open]).
 */
data class TranslationUpdate(
    val translations: Map<Int, List<String>>,
    val state: TranslationState,
    val notice: TranslationState.FallbackNotice? = null,
)

/**
 * The reader's translation engine. Translation follows the viewport: what is on screen first, then
 * the rest of a chapter the reader asked to fill, then a bounded look-ahead/behind
 * ([TranslationPlanner.windowPlan]). Already-translated paragraphs are never re-issued.
 *
 * Callers only report facts — the book, the languages and provider, whether the pane is visible, the
 * visible span — and the engine owns everything else: it remembers the last viewport, re-plans after
 * a language/provider change (which discards translations), keeps work when the pane is merely
 * hidden, gates metered providers while it is hidden, and serialises all of it.
 *
 * Threading: every public method only enqueues an event; one actor coroutine on [scope] applies
 * events and owns all state, so methods may be called from any thread. Each segment runs as a
 * child job that posts its results back as events tagged with an epoch (bumped on every reset, so a
 * late result in the old language is dropped) and a segment sequence number (so a "done" from a
 * cancelled segment cannot end its successor).
 *
 * Errors: a failure in the visible window or a chapter fill shows in the banner and stops work until
 * [retry] or a new [viewport]. A failure in the silent look-ahead/behind pauses only background work
 * until the next event — a natural back-off when a provider is down or rate-limited.
 */
class ReaderTranslationEngine(
    private val scope: CoroutineScope,
    private val translateText: TranslateTextUseCase,
) {
    private val _state = MutableStateFlow(TranslationUpdate(emptyMap(), TranslationState.Idle))
    val state: StateFlow<TranslationUpdate> = _state.asStateFlow()

    /**
     * Opens [book] with a clean slate. [start] seeds the remembered viewport (e.g. the restored
     * reading position), so the first request is that paragraph rather than the book's start.
     */
    fun open(
        book: Book,
        source: Language,
        target: Language,
        provider: TranslationProvider,
        paneVisible: Boolean,
        start: ReadingPosition = ReadingPosition.START,
    ) = send(Event.Open(book, source, target, provider, paneVisible, start))

    fun viewport(start: ReadingPosition, end: ReadingPosition) = send(Event.Viewport(start, end))

    fun settingsChanged(target: Language, provider: TranslationProvider, paneVisible: Boolean) =
        send(Event.Settings(target, provider, paneVisible))

    fun fillChapter(chapter: Int) = send(Event.Fill(chapter))

    fun retry() = send(Event.Retry)

    /**
     * The effective translator changed without the provider choice changing (e.g. an API key was
     * set or cleared): discard all translations and re-plan the remembered viewport.
     */
    fun invalidate() = send(Event.Invalidate)

    private sealed interface Event {
        data class Open(
            val book: Book, val source: Language, val target: Language,
            val provider: TranslationProvider, val paneVisible: Boolean, val start: ReadingPosition,
        ) : Event
        data class Viewport(val start: ReadingPosition, val end: ReadingPosition) : Event
        data class Settings(val target: Language, val provider: TranslationProvider, val paneVisible: Boolean) : Event
        data class Fill(val chapter: Int) : Event
        data object Retry : Event
        data object Invalidate : Event
        data class Partial(val epoch: Int, val chapter: Int, val index: Int, val text: String) : Event
        data class Downloading(val seq: Int, val progress: Float?) : Event
        data class Notice(val epoch: Int, val value: TranslationState.FallbackNotice) : Event
        data class SegmentDone(val seq: Int, val error: String?, val persistent: Boolean = false) : Event
    }

    private enum class Kind { VISIBLE, FILL, BACKGROUND }
    private data class Work(val segment: TranslationSegment, val kind: Kind)

    private val events = Channel<Event>(Channel.UNLIMITED)
    private fun send(event: Event) {
        events.trySend(event)
    }

    // ── Actor-confined state ───────────────────────────────────────────────
    private var book: Book? = null
    private var source = Language.ENGLISH
    private var target = Language.ENGLISH
    private var provider = TranslationProvider.MLKIT
    private var paneVisible = true
    private var start = ReadingPosition.START
    private var end = ReadingPosition.START
    private val results = mutableMapOf<Int, MutableList<String>>()
    private val translated = mutableMapOf<Int, MutableSet<Int>>()
    private var snapshot: Map<Int, List<String>> = emptyMap()
    private var fill: Int? = null
    private var foregroundError: String? = null
    private var foregroundErrorPersistent = false
    private var backgroundPaused = false
    private var banner: TranslationState = TranslationState.Idle
    private var notice: TranslationState.FallbackNotice? = null
    private var epoch = 0
    private var seq = 0
    private var running: Job? = null
    private var current: Work? = null
    private var doneInSegment = 0

    init {
        scope.launch {
            for (event in events) {
                handle(event)
                step()
            }
        }
    }

    private fun handle(event: Event) {
        when (event) {
            is Event.Open -> {
                cancelRunning()
                book = event.book
                source = event.source
                target = event.target
                provider = event.provider
                paneVisible = event.paneVisible
                start = event.start
                end = event.start
                discardTranslations()
            }
            is Event.Viewport -> {
                if (book == null) return
                start = event.start
                end = event.end
                // A persistent error (no/invalid key) won't resolve itself on scroll — clearing it
                // would silently re-fire the same failing request on every viewport.
                if (!foregroundErrorPersistent) foregroundError = null
                backgroundPaused = false
            }
            is Event.Settings -> {
                val reset = event.target != target || event.provider != provider
                target = event.target
                provider = event.provider
                paneVisible = event.paneVisible
                if (book == null) return
                if (reset) {
                    cancelRunning()
                    discardTranslations()
                } else if (!gateOpen()) {
                    cancelRunning()
                }
                backgroundPaused = false
            }
            is Event.Fill -> {
                val chapter = book?.chapters?.getOrNull(event.chapter) ?: return
                if (chapter.paragraphs.isNotEmpty()) fill = event.chapter
                foregroundError = null
                foregroundErrorPersistent = false
                backgroundPaused = false
            }
            Event.Retry -> {
                if (book == null) return
                foregroundError = null
                foregroundErrorPersistent = false
                backgroundPaused = false
            }
            Event.Invalidate -> {
                if (book == null) return
                cancelRunning()
                discardTranslations()
            }
            is Event.Partial -> {
                if (event.epoch != epoch) return
                val size = book?.chapters?.getOrNull(event.chapter)?.paragraphs?.size ?: return
                val list = results.getOrPut(event.chapter) { MutableList(size) { "" } }
                if (event.index in list.indices) list[event.index] = event.text
                translated.getOrPut(event.chapter) { mutableSetOf() }.add(event.index)
                snapshot = snapshot + (event.chapter to list.toList())
                val work = current
                if (work != null && work.kind != Kind.BACKGROUND) {
                    doneInSegment++
                    banner = TranslationState.Translating(progressOf(work))
                }
                publish()
            }
            is Event.Downloading -> {
                if (event.seq != seq) return
                if (current?.kind != Kind.BACKGROUND) setBanner(TranslationState.DownloadingModel(event.progress))
            }
            is Event.Notice -> {
                if (event.epoch != epoch) return
                notice = event.value
                publish()
            }
            is Event.SegmentDone -> {
                if (event.seq != seq) return
                val kind = current?.kind
                running = null
                current = null
                if (event.error != null) {
                    if (kind == Kind.BACKGROUND) {
                        backgroundPaused = true
                    } else {
                        foregroundError = event.error
                        foregroundErrorPersistent = event.persistent
                        setBanner(TranslationState.Error(event.error))
                    }
                }
            }
        }
    }

    /** Start the next piece of work if nothing is running and nothing blocks it. */
    private fun step() {
        val book = book ?: return
        if (running != null || foregroundError != null) return
        if (!gateOpen()) {
            setBanner(TranslationState.Idle)
            return
        }
        fill?.let { if (isChapterDone(it)) fill = null }
        val plan = plan(book)
        val next = if (backgroundPaused) plan.firstOrNull { it.kind != Kind.BACKGROUND } else plan.firstOrNull()
        if (next == null) {
            setBanner(TranslationState.Idle)
            return
        }
        if (next.kind == Kind.BACKGROUND && banner !is TranslationState.Idle) setBanner(TranslationState.Idle)
        launch(book, next)
    }

    /** Visible paragraphs, then the requested chapter fill (chunked), then look-ahead/behind. */
    private fun plan(book: Book): List<Work> {
        val sizes = book.chapters.map { it.paragraphs.size }
        val window = TranslationPlanner.windowPlan(
            start.chapter, start.paragraph, end.chapter, end.paragraph, sizes, ::isTranslated,
        )
        val visible = window.filter { it.foreground }.map { Work(it, Kind.VISIBLE) }
        val background = window.filterNot { it.foreground }.map { Work(it, Kind.BACKGROUND) }
        val filling = fill?.let { fillSegments(it, sizes[it]) }.orEmpty().map { Work(it, Kind.FILL) }
        return visible + filling + background
    }

    /** Untranslated runs of [chapter], cut into chunks of at most [FILL_CHUNK] so a scroll can preempt. */
    private fun fillSegments(chapter: Int, size: Int): List<TranslationSegment> {
        val out = mutableListOf<TranslationSegment>()
        var runStart = -1
        fun close(endExclusive: Int) {
            if (runStart < 0) return
            var s = runStart
            while (s < endExclusive) {
                val e = minOf(s + FILL_CHUNK, endExclusive) - 1
                out += TranslationSegment(chapter, s, e, foreground = true)
                s = e + 1
            }
            runStart = -1
        }
        for (i in 0 until size) {
            if (isTranslated(chapter, i)) close(i) else if (runStart < 0) runStart = i
        }
        close(size)
        return out
    }

    private fun launch(book: Book, work: Work) {
        val mySeq = ++seq
        val myEpoch = epoch
        current = work
        doneInSegment = 0
        if (work.kind != Kind.BACKGROUND) setBanner(TranslationState.Translating(progressOf(work)))
        val segment = work.segment
        val paragraphs = book.chapters[segment.chapterIndex].paragraphs
        val src = source
        val tgt = target
        val prov = provider
        running = scope.launch {
            var error: String? = null
            var persistent = false
            try {
                translateText(paragraphs, src, tgt, prov, segment.start, segment.endInclusive).collect { s ->
                    when (s) {
                        is TranslationState.Partial ->
                            events.send(Event.Partial(myEpoch, segment.chapterIndex, s.index, s.text))
                        is TranslationState.DownloadingModel -> events.send(Event.Downloading(mySeq, s.progress))
                        is TranslationState.FallbackNotice -> events.send(Event.Notice(myEpoch, s))
                        is TranslationState.Error -> {
                            error = s.message
                            persistent = s.persistent
                        }
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // Our own cancel: the seq has already moved on, so the SegmentDone below is ignored.
                if (!isActive) throw e
                // A stray one from below (a provider's withTimeout, a cancelled Task.await()) while we
                // are still active is a failure. Ending without one would wedge the engine, and ending
                // with no error would re-plan the same paragraph forever.
                error = e.message ?: "Translation failed"
            } catch (e: Exception) {
                // Defence-in-depth, not demonstrated by a test: the use case catches Exception itself.
                error = e.message ?: "Translation failed"
            } finally {
                events.trySend(Event.SegmentDone(mySeq, error, persistent))
            }
        }
    }

    private fun cancelRunning() {
        running?.cancel()
        running = null
        current = null
        seq++ // a SegmentDone already queued by the cancelled job must not end the next one
    }

    private fun discardTranslations() {
        epoch++
        results.clear()
        translated.clear()
        snapshot = emptyMap()
        fill = null
        foregroundError = null
        foregroundErrorPersistent = false
        backgroundPaused = false
        banner = TranslationState.Idle
        notice = null
        publish()
    }

    private fun gateOpen(): Boolean = !provider.requiresNetwork || paneVisible

    private fun isTranslated(chapter: Int, para: Int): Boolean = translated[chapter]?.contains(para) == true

    private fun isChapterDone(chapter: Int): Boolean {
        val size = book?.chapters?.getOrNull(chapter)?.paragraphs?.size ?: return true
        return (translated[chapter]?.size ?: 0) >= size
    }

    private fun progressOf(work: Work): Int = when (work.kind) {
        Kind.FILL -> {
            val size = book?.chapters?.getOrNull(work.segment.chapterIndex)?.paragraphs?.size ?: 0
            if (size == 0) 0 else ((translated[work.segment.chapterIndex]?.size ?: 0) * 100 / size).coerceIn(0, 100)
        }
        else -> {
            val total = (work.segment.endInclusive - work.segment.start + 1).coerceAtLeast(1)
            (doneInSegment * 100 / total).coerceIn(0, 100)
        }
    }

    private fun setBanner(state: TranslationState) {
        if (banner == state) return
        banner = state
        publish()
    }

    private fun publish() {
        _state.value = TranslationUpdate(snapshot, banner, notice)
    }

    private companion object {
        /** Chapter-fill chunk size: small enough that a new viewport preempts within one chunk. */
        const val FILL_CHUNK = 8
    }
}
