package com.example.splitreader.domain.model

/**
 * Where the reader is in a book (see CONTEXT.md "Reading position"): a chapter, a paragraph
 * within it (index into [Chapter.paragraphs]) and a pixel offset into that paragraph. Mastheads
 * and illustrations are never positions. See docs/adr/0001.
 */
data class ReadingPosition(val chapter: Int, val paragraph: Int, val offset: Int = 0) {
    companion object {
        val START = ReadingPosition(0, 0, 0)
    }
}

/**
 * The nearest position that exists in [book]. Returns `this` when already valid. Otherwise
 * clamps chapter and paragraph and drops the offset, which belonged to a different paragraph.
 */
fun ReadingPosition.coercedTo(book: Book): ReadingPosition {
    if (book.chapters.isEmpty()) return ReadingPosition.START
    val ch = chapter.coerceIn(0, book.chapters.lastIndex)
    val lastParagraph = (book.chapters[ch].paragraphs.size - 1).coerceAtLeast(0)
    val p = paragraph.coerceIn(0, lastParagraph)
    return if (ch == chapter && p == paragraph) this else ReadingPosition(ch, p, 0)
}

/** [position]'s paragraph counted through the whole book, from 0. */
fun Book.paragraphOrdinal(position: ReadingPosition): Int {
    val p = position.coercedTo(this)
    return chapters.take(p.chapter).sumOf { it.paragraphs.size } + p.paragraph
}
