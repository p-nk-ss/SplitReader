package com.example.splitreader.presentation.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.model.ReadingPosition

/**
 * Pure mirror of [bookItems]' emission order for index arithmetic: converts between LazyColumn
 * item indices and (chapter, paragraph) coordinates. Lives next to [bookItems] because the two
 * must agree item-for-item; any change to the emission order must change both.
 */
internal class BookItemIndex(book: Book, showIllustrations: Boolean) {
    /** Item index of each chapter's masthead, by chapter. */
    private val chapterStarts = mutableListOf<Int>()

    /** Coordinate of the paragraph at each item index; null for masthead/image/padding items. */
    private val paragraphByItem: List<Pair<Int, Int>?> = buildList {
        book.chapters.forEachIndexed { chapterIndex, chapter ->
            chapterStarts += size
            add(null) // masthead
            val images = if (showIllustrations) chapter.images else emptyList()
            chapter.paragraphs.forEachIndexed { idx, _ ->
                images.forEach { img -> if (img.anchorParagraph == idx) add(null) }
                add(chapterIndex to idx)
            }
            images.forEach { img -> if (img.anchorParagraph >= chapter.paragraphs.size) add(null) }
        }
        add(null) // end_padding
    }

    private val itemByParagraph: Map<Pair<Int, Int>, Int> = buildMap {
        paragraphByItem.forEachIndexed { item, coord -> if (coord != null) put(coord, item) }
    }

    /** For each item, the paragraph at or below it (last paragraph past the book end). */
    private val atOrAfter: List<Pair<Int, Int>?> = run {
        val out = arrayOfNulls<Pair<Int, Int>>(paragraphByItem.size)
        var next: Pair<Int, Int>? = null
        for (i in paragraphByItem.indices.reversed()) {
            paragraphByItem[i]?.let { next = it }
            out[i] = next
        }
        val last = paragraphByItem.lastOrNull { it != null }
        out.map { it ?: last }
    }

    /** For each item, the paragraph at or above it (first paragraph before the book start). */
    private val atOrBefore: List<Pair<Int, Int>?> = run {
        val out = arrayOfNulls<Pair<Int, Int>>(paragraphByItem.size)
        var prev: Pair<Int, Int>? = null
        for (i in paragraphByItem.indices) {
            paragraphByItem[i]?.let { prev = it }
            out[i] = prev
        }
        val first = paragraphByItem.firstOrNull { it != null }
        out.map { it ?: first }
    }

    /** Item index of paragraph [paragraph] in chapter [chapter]; its chapter's masthead if absent. */
    fun itemIndexOf(chapter: Int, paragraph: Int): Int =
        itemByParagraph[chapter to paragraph] ?: chapterStartItem(chapter)

    /** Item index of [chapter]'s masthead, clamped to the book (0 for a book with no chapters). */
    fun chapterStartItem(chapter: Int): Int =
        if (chapterStarts.isEmpty()) 0 else chapterStarts[chapter.coerceIn(chapterStarts.indices)]

    /**
     * The Reading position for a list whose first visible item is [itemIndex], scrolled
     * [itemOffset] px into it. A paragraph item keeps the offset. A masthead, illustration or
     * padding item names the first paragraph below it (offset 0). See CONTEXT.md.
     */
    fun positionAt(itemIndex: Int, itemOffset: Int): ReadingPosition {
        paragraphByItem.getOrNull(itemIndex)?.let { (ch, p) -> return ReadingPosition(ch, p, itemOffset) }
        val (ch, p) = paragraphAtOrAfter(itemIndex)
        return ReadingPosition(ch, p, 0)
    }

    /** The paragraph shown at [itemIndex], or the nearest one below it (clamped at book end). */
    fun paragraphAtOrAfter(itemIndex: Int): Pair<Int, Int> =
        atOrAfter.getOrNull(itemIndex.coerceIn(atOrAfter.indices)) ?: (0 to 0)

    /** The paragraph shown at [itemIndex], or the nearest one above it (clamped at book start). */
    fun paragraphAtOrBefore(itemIndex: Int): Pair<Int, Int> =
        atOrBefore.getOrNull(itemIndex.coerceIn(atOrBefore.indices)) ?: (0 to 0)
}

/**
 * The single source of the reader's LazyColumn item structure — keys, order, and count.
 *
 * Both the landscape spread and each of the two vertical panes call this, so their item indices
 * are identical **by construction** rather than by anyone keeping two emitters in step. That
 * identity is what lets every item↔Reading-position conversion (`BookItemIndex`) run on the
 * top pane's `listState` alone.
 *
 * Consequence worth knowing before "optimising": the translation pane MUST emit an item for every
 * illustration too, even though it only draws a slim placeholder. Skipping them would shift every
 * subsequent index in that pane and silently desynchronise the two lists.
 *
 * This function owns the `item()` calls and their keys and nothing else; the three slots own
 * their content entirely, including their own trailing spacing.
 */
internal fun LazyListScope.bookItems(
    book: Book,
    showIllustrations: Boolean,
    masthead: @Composable (chapterIndex: Int, chapter: Chapter) -> Unit,
    image: @Composable (chapterIndex: Int, imageIndex: Int, image: ChapterImage) -> Unit,
    paragraph: @Composable (chapterIndex: Int, paragraphIndex: Int, text: String) -> Unit,
) {
    book.chapters.forEachIndexed { chapterIndex, chapter ->
        item(key = "masthead_$chapterIndex") { masthead(chapterIndex, chapter) }

        val images = if (showIllustrations) chapter.images else emptyList()
        chapter.paragraphs.forEachIndexed { idx, original ->
            images.forEachIndexed { imgIdx, img ->
                if (img.anchorParagraph == idx) {
                    item(key = "img_${chapterIndex}_$imgIdx") { image(chapterIndex, imgIdx, img) }
                }
            }
            item(key = "p_${chapterIndex}_$idx") { paragraph(chapterIndex, idx, original) }
        }
        images.forEachIndexed { imgIdx, img ->
            if (img.anchorParagraph >= chapter.paragraphs.size) {
                item(key = "img_${chapterIndex}_$imgIdx") { image(chapterIndex, imgIdx, img) }
            }
        }
    }
    item(key = "end_padding") { Spacer(Modifier.height(48.dp)) }
}
