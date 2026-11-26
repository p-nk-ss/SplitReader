package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language

/**
 * Words whose full stop does not end a sentence ("Mrs. Long"), in Moses' nonbreaking_prefix format:
 * one word per line, `#` comments, and `word #NUMERIC_ONLY#` for words that hold only before a number
 * ("No. 5"). The same lists Offline HQ reads, so both splitters agree.
 */
class Abbreviations(val always: Set<String>, val beforeNumber: Set<String>) {
    companion object {
        val NONE = Abbreviations(emptySet(), emptySet())

        fun parse(text: String): Abbreviations {
            val always = mutableSetOf<String>()
            val beforeNumber = mutableSetOf<String>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if ("#NUMERIC_ONLY#" in line) beforeNumber += line.substringBefore("#").trim()
                else always += line.substringBefore("#").trim()
            }
            return Abbreviations(always, beforeNumber)
        }
    }

    /** Whether the full stop ending [sentence] belongs to an abbreviation, given the text that follows. */
    fun holds(sentence: String, next: String): Boolean {
        val s = sentence.trimEnd()
        if (!s.endsWith(".")) return false
        val word = s.dropLast(1).takeLastWhile { !it.isWhitespace() }.trimStart { !it.isLetterOrDigit() }
        if (word.isEmpty()) return false
        return word in always || (word in beforeNumber && next.trimStart().firstOrNull()?.isDigit() == true)
    }
}

/** The [Abbreviations] of a source language; [Abbreviations.NONE] when there is no list. */
fun interface AbbreviationSource {
    fun forLanguage(language: Language): Abbreviations
}
