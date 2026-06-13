package com.example.splitreader.presentation.reader

/** A contiguous run of paragraphs in one chapter scheduled for translation. */
data class TranslationSegment(
    val chapterIndex: Int,
    val start: Int,
    val endInclusive: Int,
    val foreground: Boolean,
)

/**
 * Pure scheduling logic for the reader's translation pipeline. Translation follows the *viewport*:
 * everything currently on screen (which may span several short chapters) is translated first, then
 * a small look-ahead below and look-behind above. The window is bounded, so work stays small and
 * predictable regardless of chapter sizes — and because it tracks the real visible range rather than
 * a single chapter anchored at one scroll point, scrolling up or flinging through short chapters
 * always translates what the reader can actually see.
 *
 * Keeping this logic side-effect-free makes the ordering, clamping, and pruning rules unit-testable
 * without coroutines or Android.
 */
object TranslationPlanner {
    /** Paragraphs translated below the last visible paragraph (forward look-ahead). */
    const val LOOKAHEAD = 8

    /** Paragraphs translated above the first visible paragraph (backward look-behind). */
    const val LOOKBEHIND = 4

    /**
     * Plan the translation window for the given visible paragraph span.
     *
     * The visible range may cross chapter boundaries (`visibleStartChapter` may differ from
     * `visibleEndChapter`). The result is an ordered list of per-chapter segments: the visible
     * paragraphs first (foreground — these drive the progress banner), then the look-ahead below,
     * then the look-behind above. Paragraphs for which [isTranslated] returns true are pruned, so a
     * segment is split around text that is already done and the engine never re-issues cached work.
     */
    fun windowPlan(
        visibleStartChapter: Int,
        visibleStartPara: Int,
        visibleEndChapter: Int,
        visibleEndPara: Int,
        chapterSizes: List<Int>,
        isTranslated: (chapter: Int, para: Int) -> Boolean,
    ): List<TranslationSegment> {
        val total = chapterSizes.sum()
        if (total <= 0) return emptyList()

        val vStart = toGlobal(visibleStartChapter, visibleStartPara, chapterSizes).coerceIn(0, total - 1)
        val vEnd = toGlobal(visibleEndChapter, visibleEndPara, chapterSizes)
            .coerceIn(0, total - 1)
            .coerceAtLeast(vStart)

        val lookaheadStart = vEnd + 1
        val lookaheadEnd = (vEnd + LOOKAHEAD).coerceAtMost(total - 1)
        val lookbehindEnd = vStart - 1
        val lookbehindStart = (vStart - LOOKBEHIND).coerceAtLeast(0)

        val segments = mutableListOf<TranslationSegment>()
        segments += buildSegments(vStart, vEnd, foreground = true, chapterSizes, isTranslated)
        if (lookaheadStart <= lookaheadEnd) {
            segments += buildSegments(lookaheadStart, lookaheadEnd, foreground = false, chapterSizes, isTranslated)
        }
        if (lookbehindStart <= lookbehindEnd) {
            segments += buildSegments(lookbehindStart, lookbehindEnd, foreground = false, chapterSizes, isTranslated)
        }
        return segments
    }

    /** Monotonic 0..100 progress within a single segment of [total] paragraphs. */
    fun progressPercent(done: Int, total: Int): Int =
        if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)

    /**
     * Whether translation should run at all. ML Kit is free/on-device, so it keeps translating even
     * when the translation pane is hidden; paid providers translate only while the pane is visible,
     * so hiding it never burns subscription quota/tokens on text the reader can't see.
     */
    fun shouldTranslate(isMlKit: Boolean, translationVisible: Boolean): Boolean =
        isMlKit || translationVisible

    /**
     * Walk the inclusive global paragraph range [from]..[to], dropping already-translated paragraphs,
     * and coalesce the rest into contiguous per-chapter segments (broken at chapter boundaries and at
     * gaps left by pruned paragraphs). Segment order follows the walk, so reading order is preserved.
     */
    private fun buildSegments(
        from: Int,
        to: Int,
        foreground: Boolean,
        sizes: List<Int>,
        isTranslated: (Int, Int) -> Boolean,
    ): List<TranslationSegment> {
        val out = mutableListOf<TranslationSegment>()
        if (from > to) return out

        var segChapter = -1
        var segStart = -1
        var segEnd = -1
        fun close() {
            if (segChapter >= 0) out += TranslationSegment(segChapter, segStart, segEnd, foreground)
            segChapter = -1
            segStart = -1
            segEnd = -1
        }

        for (global in from..to) {
            val (chapter, para) = toLocal(global, sizes)
            if (isTranslated(chapter, para)) {
                close()
                continue
            }
            if (segChapter == chapter && para == segEnd + 1) {
                segEnd = para
            } else {
                close()
                segChapter = chapter
                segStart = para
                segEnd = para
            }
        }
        close()
        return out
    }

    /** Convert a (chapter, paragraph) coordinate to a flat global paragraph index. */
    private fun toGlobal(chapter: Int, para: Int, sizes: List<Int>): Int {
        val ch = chapter.coerceIn(0, (sizes.size - 1).coerceAtLeast(0))
        var offset = 0
        for (i in 0 until ch) offset += sizes[i]
        return offset + para.coerceAtLeast(0)
    }

    /** Convert a flat global paragraph index back to a (chapter, paragraph) coordinate. */
    private fun toLocal(global: Int, sizes: List<Int>): Pair<Int, Int> {
        var remaining = global
        for (i in sizes.indices) {
            val size = sizes[i]
            if (remaining < size) return i to remaining
            remaining -= size
        }
        val last = sizes.lastIndex.coerceAtLeast(0)
        return last to (sizes.getOrElse(last) { 1 } - 1).coerceAtLeast(0)
    }
}
