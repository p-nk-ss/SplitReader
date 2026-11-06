package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SourceDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Detects the language a book is written in: identifies [SAMPLES] stretches of prose taken evenly
 * across the whole book and takes the majority.
 *
 * One stretch from the start was not enough (found with 25 books, 2026-11-04): Don Quixote opens with
 * Latin epigraphs and Rashomon with an English digitizer's note, and both came out English. A vote
 * across the book outweighs such a start, a quoted passage or a licence at the end.
 */
class DetectBookLanguageUseCase @Inject constructor(
    private val detector: LanguageDetector,
) {
    suspend operator fun invoke(book: Book): SourceDetection {
        // Joins the whole book's text: off the main thread (loadBook runs on viewModelScope).
        val samples = withContext(Dispatchers.Default) { languageSamples(book) }
        val votes = samples.mapNotNull { sample -> detector.identify(sample)?.let(SourceDetection::normalizeTag) }
        if (votes.isEmpty()) return SourceDetection.Unknown
        // A tie goes to the language seen first, i.e. nearer the book's start: eachCount keeps
        // first-seen order and maxBy takes the first maximum. It takes a 3/3/1 split to matter.
        val winner = votes.groupingBy { it }.eachCount().maxBy { it.value }.key
        return SourceDetection.fromTag(winner)
    }

    companion object {
        const val SAMPLES = 7
        /** Long enough for a confident answer, short enough that one stretch stays in one language. */
        const val SAMPLE_CHARS = 300
        /** Shorter paragraphs are mostly headings, names and dialogue tags, which mislead the detector. */
        const val MIN_PARAGRAPH = 40

        /**
         * Up to [SAMPLES] stretches of [SAMPLE_CHARS] characters, spread evenly over the book's text by
         * length, not by paragraph count: Rashomon is two English notes and then the whole story as
         * one 6,000-character paragraph, and spread over paragraphs the notes won two votes of three.
         *
         * From paragraphs of at least [MIN_PARAGRAPH] characters, unless there is too little of them
         * (verse, plays): then every line. The first and last 5% of the text are skipped when it is
         * long enough (front matter, licences). Empty only for a book with no text at all.
         */
        fun languageSamples(book: Book): List<String> {
            val lines = book.chapters.flatMap { it.paragraphs }.map { it.trim() }.filter { it.isNotEmpty() }
            // Weighted: 30 kana and kanji are a long line (a book of short Japanese poems and a long English
            // note came out English when only the note counted as prose).
            val long = lines.filter { ParagraphLanguage.weightedLength(it, cap = MIN_PARAGRAPH) >= MIN_PARAGRAPH }
            // Verse and plays are short lines; with too little long prose, sample every line instead.
            val prose = if (long.sumOf { ParagraphLanguage.weightedLength(it) } >= SAMPLES * SAMPLE_CHARS / 2) long else lines
            if (prose.isEmpty()) return emptyList()
            val text = prose.joinToString("\n")
            val trim = if (text.length >= 2 * SAMPLES * SAMPLE_CHARS) text.length / 20 else 0
            val span = text.length - 2 * trim
            val count = (span / SAMPLE_CHARS).coerceIn(1, SAMPLES)
            return List(count) { k ->
                val start = trim + k * span / count
                text.substring(start, minOf(start + SAMPLE_CHARS, text.length))
            }
        }
    }
}
