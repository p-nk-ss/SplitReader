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
 * them. Only Chinese and Japanese sources are split, plus any paragraph too long to send whole;
 * everything else goes to the engine unchanged, as before.
 */
object Sentences {
    /** The longest piece sent to an engine; a longer sentence is cut at a comma or a space. */
    const val MAX_PIECE = 400

    /** Any paragraph longer than this is split whatever its language: no engine takes it well whole. */
    const val LONG = 1500

    private val UNSPACED = setOf(Language.CHINESE, Language.JAPANESE)

    fun needsSplitting(text: String, source: Language): Boolean = source in UNSPACED || text.length > LONG

    /** The pieces of [text], in order; concatenated they are [text] exactly (minus blank runs between sentences). */
    fun split(text: String, source: Language, maxChars: Int = MAX_PIECE): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag(source.code))
        iterator.setText(text)
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            sentences += text.substring(start, end)
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
            val at = window.indexOfLast { it in BREAKS }.takeIf { it >= maxChars / 2 }?.plus(1) ?: maxChars
            pieces += rest.substring(0, at)
            rest = rest.substring(at)
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    private const val BREAKS = "，、；：,;: 　"
}
