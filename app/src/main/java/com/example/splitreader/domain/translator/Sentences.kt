package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language
import java.text.BreakIterator
import java.util.Locale

/**
 * Splits a paragraph into sentences for the on-device engines, and joins their translations.
 *
 * Offline HQ splits sentences itself, but not on "。" (found on a tablet emulator, 2026-11-04): a
 * Chinese chapter written as one paragraph went in as a single "sentence" and ran for minutes, and
 * Japanese sentences came back glued together ("упал.Тот"), since the source has no spaces between
 * them. Chinese and Japanese sources are always split.
 *
 * Any other paragraph longer than [MAX_PIECE] is split too (translation eval 2026-11-25): Offline HQ
 * dropped the end or the middle of sentences of ~600 chars (Melville, London) — the model itself,
 * not its 128-token break — while pieces of at most 300 came back whole. A full stop after a known
 * abbreviation ([Abbreviations], the lists Offline HQ reads) is not a break: "Mrs. | Long".
 */
object Sentences {
    /** The longest piece sent to an on-device engine; a longer sentence is cut at a comma or a space. */
    const val MAX_PIECE = 300

    private val UNSPACED = setOf(Language.CHINESE, Language.JAPANESE)

    fun needsSplitting(text: String, source: Language): Boolean = source in UNSPACED || text.length > MAX_PIECE

    /** The pieces of [text], in order; concatenated they are [text] exactly (minus blank runs between sentences). */
    fun split(
        text: String,
        source: Language,
        maxChars: Int = MAX_PIECE,
        abbreviations: Abbreviations = Abbreviations.NONE,
    ): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag(source.code))
        iterator.setText(text)
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end)
            if (sentences.isNotEmpty() && abbreviations.holds(sentences.last(), sentence)) {
                sentences[sentences.lastIndex] = sentences.last() + sentence
            } else {
                sentences += sentence
            }
            start = end
            end = iterator.next()
        }
        return sentences.flatMap { cut(it, maxChars) }.filter { it.isNotBlank() }
    }

    /** Translated pieces as one paragraph: spaced, except into a language written without spaces. */
    fun join(pieces: List<String>, target: Language): String =
        pieces.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(if (target in UNSPACED) "" else " ")

    private fun cut(sentence: String, maxChars: Int): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)
        val pieces = mutableListOf<String>()
        var rest = sentence
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars)
            // Strongest break in the second half of the window: a clause, then a comma, then a space.
            // A cut at a plain space mid-clause ("to take | possession") comes back with a full stop.
            var at = BREAKS.firstNotNullOfOrNull { tier ->
                window.indexOfLast { it in tier }.takeIf { it >= maxChars / 2 }?.plus(1)
            } ?: maxChars
            // Never between the halves of a character outside the BMP (rarer CJK ideographs):
            // the engine would get, and give back, two broken halves ("�").
            if (Character.isHighSurrogate(rest[at - 1]) && Character.isLowSurrogate(rest[at])) at--
            pieces += rest.substring(0, at)
            rest = rest.substring(at)
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    private val BREAKS = listOf("；：;:—", "，、,", " 　")
}
