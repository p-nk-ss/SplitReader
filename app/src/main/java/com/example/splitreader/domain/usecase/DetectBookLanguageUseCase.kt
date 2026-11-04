package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SourceDetection
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
        val votes = languageSamples(book)
            .mapNotNull { detector.identify(it)?.substringBefore('-')?.lowercase() }
        if (votes.isEmpty()) return SourceDetection.Unknown
        // A tie goes to the language seen first: eachCount keeps first-seen order, maxBy takes the first maximum.
        val winner = votes.groupingBy { it }.eachCount().maxBy { it.value }.key
        return Language.fromCodeOrNull(winner)?.let { SourceDetection.Supported(it) }
            ?: SourceDetection.Unsupported(winner)
    }

    companion object {
        const val SAMPLES = 7
        /** Long enough for a confident answer, short enough that one stretch stays in one language. */
        const val SAMPLE_CHARS = 300
        /** Shorter paragraphs are mostly headings, names and dialogue tags, which mislead the detector. */
        const val MIN_PARAGRAPH = 40

        /**
         * [SAMPLES] stretches of prose, each about [SAMPLE_CHARS] long, spread evenly across the book.
         * Paragraphs of at least [MIN_PARAGRAPH] characters, unless there is too little of them (verse,
         * plays): then every line. The first and last 5% are skipped when the book is long enough:
         * front matter and licences. Empty only for a book with no text at all.
         */
        fun languageSamples(book: Book): List<String> {
            val lines = book.chapters.flatMap { it.paragraphs }.map { it.trim() }.filter { it.isNotEmpty() }
            val long = lines.filter { it.length >= MIN_PARAGRAPH }
            // Verse and plays are short lines; with too little long prose, sample every line instead.
            val prose = if (long.sumOf { it.length } >= SAMPLES * SAMPLE_CHARS / 2) long else lines
            if (prose.isEmpty()) return emptyList()
            val trim = if (prose.size >= 40) prose.size / 20 else 0
            val body = prose.subList(trim, prose.size - trim)
            val count = minOf(SAMPLES, body.size)
            return List(count) { k ->
                val sample = StringBuilder()
                var i = k * body.size / count
                while (i < body.size && sample.length < SAMPLE_CHARS) {
                    if (sample.isNotEmpty()) sample.append(' ')
                    sample.append(body[i])
                    i++
                }
                sample.take(SAMPLE_CHARS * 2).toString()
            }
        }
    }
}
