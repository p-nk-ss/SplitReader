package com.example.splitreader.presentation.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records the keys `bookItems` emits, in order. Content lambdas are never invoked. */
private class KeyRecordingScope : LazyListScope {
    val keys = mutableListOf<Any?>()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        keys += key
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) = throw NotImplementedError("bookItems must emit one item() per entry so keys stay addressable")

    @OptIn(ExperimentalFoundationApi::class)
    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) = throw NotImplementedError("bookItems emits no sticky headers")
}

private fun emittedKeys(book: Book, showIllustrations: Boolean): List<Any?> =
    KeyRecordingScope().also {
        it.bookItems(
            book = book,
            showIllustrations = showIllustrations,
            masthead = { _, _ -> },
            image = { _, _, _ -> },
            paragraph = { _, _, _ -> },
        )
    }.keys

/**
 * Two chapters with inline + trailing illustrations. Emission order (illustrations on):
 *
 *  0 masthead_0
 *  1 p_0_0
 *  2 img_0_0      (anchored at p1 -> emitted just before it)
 *  3 p_0_1
 *  4 p_0_2
 *  5 img_0_1      (anchorParagraph == size -> trailing)
 *  6 masthead_1
 *  7 img_1_0      (anchored at p0)
 *  8 p_1_0
 *  9 p_1_1
 * 10 end_padding
 */
private val illustrated = Book(
    title = "T", author = "A", filePath = "/x",
    chapters = listOf(
        Chapter(
            index = 0,
            title = "One",
            paragraphs = listOf("a", "b", "c"),
            images = listOf(
                ChapterImage(anchorParagraph = 1, path = "/img/inline.png"),
                ChapterImage(anchorParagraph = 3, path = "/img/trailing.png"),
            ),
        ),
        Chapter(
            index = 1,
            title = "Two",
            paragraphs = listOf("d", "e"),
            images = listOf(
                ChapterImage(anchorParagraph = 0, path = "/img/lead.png"),
            ),
        ),
    ),
)

class BookItemIndexTest {

    private val index = BookItemIndex(illustrated, showIllustrations = true)

    @Test
    fun `every paragraph item maps back to its own coordinate in both directions`() {
        val keys = emittedKeys(illustrated, showIllustrations = true)
        for ((itemIndex, key) in keys.withIndex()) {
            val k = key as String
            if (!k.startsWith("p_")) continue
            val (chapter, para) = k.removePrefix("p_").split("_").map(String::toInt)
            assertEquals("atOrAfter($k)", chapter to para, index.paragraphAtOrAfter(itemIndex))
            assertEquals("atOrBefore($k)", chapter to para, index.paragraphAtOrBefore(itemIndex))
        }
    }

    @Test
    fun `an inline image above a paragraph does not shift the reported window`() {
        // Item 3 is p_0_1 (one image item sits above it). The naive `itemIndex - chapterStart - 1`
        // arithmetic reports paragraph 2 here — the off-by-images bug that starves the top visible
        // paragraphs out of the translation plan.
        assertEquals(0 to 1, index.paragraphAtOrAfter(3))
        assertEquals(0 to 1, index.paragraphAtOrBefore(3))
    }

    @Test
    fun `an image item resolves to its anchor paragraph looking down and the previous one looking up`() {
        assertEquals(0 to 1, index.paragraphAtOrAfter(2)) // img_0_0 sits just above p_0_1
        assertEquals(0 to 0, index.paragraphAtOrBefore(2))
    }

    @Test
    fun `a masthead resolves to the chapter's first paragraph looking down and the previous chapter's last looking up`() {
        assertEquals(1 to 0, index.paragraphAtOrAfter(6)) // masthead_1
        assertEquals(0 to 2, index.paragraphAtOrBefore(6))
    }

    @Test
    fun `book edges clamp instead of running off the ends`() {
        assertEquals(0 to 0, index.paragraphAtOrBefore(0)) // masthead_0: nothing above -> clamp
        assertEquals(1 to 1, index.paragraphAtOrAfter(10)) // end_padding: nothing below -> clamp
        assertEquals(1 to 1, index.paragraphAtOrBefore(10))
    }

    @Test
    fun `a trailing image belongs to the gap between chapters`() {
        assertEquals(1 to 0, index.paragraphAtOrAfter(5)) // img_0_1 after last paragraph of ch 0
        assertEquals(0 to 2, index.paragraphAtOrBefore(5))
    }

    @Test
    fun `itemIndexOf lands exactly on the paragraph's real item for bookmark jumps`() {
        val keys = emittedKeys(illustrated, showIllustrations = true)
        assertEquals("p_0_1", keys[index.itemIndexOf(0, 1)])
        assertEquals("p_0_2", keys[index.itemIndexOf(0, 2)])
        assertEquals("p_1_0", keys[index.itemIndexOf(1, 0)])
        assertEquals("p_1_1", keys[index.itemIndexOf(1, 1)])
    }

    @Test
    fun `illustrations off collapses the image items out of the arithmetic`() {
        val plain = BookItemIndex(illustrated, showIllustrations = false)
        val keys = emittedKeys(illustrated, showIllustrations = false)
        assertEquals("p_0_1", keys[plain.itemIndexOf(0, 1)])
        assertEquals("p_1_1", keys[plain.itemIndexOf(1, 1)])
        assertEquals(1 to 0, plain.paragraphAtOrAfter(keys.indexOf("p_1_0")))
    }

    @Test
    fun `an empty chapter is skipped through in both directions`() {
        val withEmpty = Book(
            title = "T", author = "A", filePath = "/x",
            chapters = listOf(
                Chapter(index = 0, title = "One", paragraphs = listOf("a"), images = emptyList()),
                Chapter(index = 1, title = "Hollow", paragraphs = emptyList(), images = emptyList()),
                Chapter(index = 2, title = "Three", paragraphs = listOf("b"), images = emptyList()),
            ),
        )
        val idx = BookItemIndex(withEmpty, showIllustrations = true)
        // Items: 0 masthead_0, 1 p_0_0, 2 masthead_1, 3 masthead_2, 4 p_2_0, 5 end_padding
        assertEquals(2 to 0, idx.paragraphAtOrAfter(2)) // empty chapter's masthead -> next real paragraph
        assertEquals(0 to 0, idx.paragraphAtOrBefore(2))
    }
}
