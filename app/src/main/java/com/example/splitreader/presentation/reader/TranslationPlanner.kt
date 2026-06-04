package com.example.splitreader.presentation.reader

/** A contiguous run of paragraphs in one chapter scheduled for translation. */
data class TranslationSegment(
    val chapterIndex: Int,
    val start: Int,
    val endInclusive: Int,
    val foreground: Boolean,
)

/**
 * Pure scheduling logic for the reader's translation pipeline. All engines (ML Kit and paid)
 * share one model: a high-priority foreground window anchored at the visible paragraph, plus
 * a provider-specific background fill. Keeping this logic side-effect-free makes the ordering,
 * progress, and prefetch rules unit-testable without coroutines or Android.
 */
object TranslationPlanner {
    /** Paragraphs translated immediately around the visible position (foreground, all engines). */
    const val VISIBLE_WINDOW = 12

    /** How far ahead of scroll paid providers keep text translated. */
    const val LOOKAHEAD = 20

    /** Trigger the next paid prefetch batch when within this many paragraphs of the edge. */
    const val PREFETCH_TRIGGER = 5

    /** The foreground (visible) range: [anchor, anchor+VISIBLE_WINDOW) clamped to the chapter. */
    fun foregroundRange(anchor: Int, chapterSize: Int): IntRange {
        if (chapterSize <= 0) return IntRange.EMPTY
        val start = anchor.coerceIn(0, chapterSize - 1)
        val end = (start + VISIBLE_WINDOW - 1).coerceAtMost(chapterSize - 1)
        return start..end
    }

    /**
     * Background fill plan after the foreground window has been scheduled.
     *
     * ML Kit (free, on-device): rest of the current chapter, then the whole next chapter, then
     * the start of the current chapter (paragraphs above the anchor). Paid providers: empty —
     * they only extend via scroll prefetch ([prefetchRange]) or an explicit whole-chapter request.
     */
    fun backgroundPlan(
        isMlKit: Boolean,
        chapterIndex: Int,
        foregroundEnd: Int,
        anchor: Int,
        chapterSizes: List<Int>,
    ): List<TranslationSegment> {
        if (!isMlKit) return emptyList()
        val size = chapterSizes.getOrElse(chapterIndex) { 0 }
        if (size <= 0) return emptyList()
        val segments = mutableListOf<TranslationSegment>()
        if (foregroundEnd < size - 1) {
            segments += TranslationSegment(chapterIndex, foregroundEnd + 1, size - 1, foreground = false)
        }
        val next = chapterIndex + 1
        val nextSize = chapterSizes.getOrElse(next) { 0 }
        if (next <= chapterSizes.lastIndex && nextSize > 0) {
            segments += TranslationSegment(next, 0, nextSize - 1, foreground = false)
        }
        val clampedAnchor = anchor.coerceIn(0, size - 1)
        if (clampedAnchor > 0) {
            segments += TranslationSegment(chapterIndex, 0, clampedAnchor - 1, foreground = false)
        }
        return segments
    }

    /** True when scroll has approached the translated edge and more should be prefetched (paid). */
    fun needsPrefetch(edge: Int, scrollPos: Int, chapterSize: Int): Boolean =
        edge < chapterSize - 1 && edge - scrollPos <= PREFETCH_TRIGGER

    /** Next prefetch range ahead of scroll, or null when nothing new is ahead of the edge. */
    fun prefetchRange(edge: Int, scrollPos: Int, chapterSize: Int): IntRange? {
        if (chapterSize <= 0) return null
        val targetEnd = (scrollPos + LOOKAHEAD).coerceAtMost(chapterSize - 1)
        if (targetEnd <= edge) return null
        return (edge + 1)..targetEnd
    }

    /** Monotonic 0..100 progress within a single segment of [total] paragraphs. */
    fun progressPercent(done: Int, total: Int): Int =
        if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)
}
