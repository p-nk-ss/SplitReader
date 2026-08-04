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
private class RecordingLazyListScope : LazyListScope {
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

private fun emit(book: Book, showIllustrations: Boolean): List<Any?> =
    RecordingLazyListScope().also {
        it.bookItems(
            book = book,
            showIllustrations = showIllustrations,
            masthead = { _, _ -> },
            image = { _, _, _ -> },
            paragraph = { _, _, _ -> },
        )
    }.keys

/** One chapter, three paragraphs, an image anchored inline at p1 and one anchored past the end. */
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
    ),
)

class BookItemsTest {

    @Test
    fun `an inline image is emitted before its anchor paragraph and a trailing one after the last`() {
        assertEquals(
            listOf(
                "masthead_0",
                "p_0_0",
                "img_0_0",   // anchored at paragraph 1 -> emitted immediately before it
                "p_0_1",
                "p_0_2",
                "img_0_1",   // anchorParagraph == paragraphs.size -> after the last paragraph
                "end_padding",
            ),
            emit(illustrated, showIllustrations = true),
        )
    }

    /**
     * The crash guard. Compose throws on a duplicate key within one LazyListScope, so an image
     * satisfying both the inline and the trailing filter takes the reader screen down on any real
     * illustrated book. Nothing else in the suite would notice.
     */
    @Test
    fun `no key is emitted twice`() {
        val keys = emit(illustrated, showIllustrations = true)
        assertEquals(
            "Duplicate keys would crash the reader: $keys",
            keys.size, keys.toSet().size,
        )
    }

    @Test
    fun `illustrations off removes the image items entirely rather than emitting empty ones`() {
        assertEquals(
            listOf("masthead_0", "p_0_0", "p_0_1", "p_0_2", "end_padding"),
            emit(illustrated, showIllustrations = false),
        )
    }

    /**
     * `chapterItemStarts` in ReaderScreen computes chapter offsets as
     * `1 + paragraphs.size + (images.size if shown)`. Scroll restore and bookmark jumps are wrong
     * by exactly the difference if the real emission disagrees with that arithmetic.
     */
    @Test
    fun `the emitted count per chapter matches what chapterItemStarts assumes`() {
        val chapter = illustrated.chapters[0]
        val expectedPerChapter = 1 + chapter.paragraphs.size + chapter.images.size
        assertEquals(expectedPerChapter, emit(illustrated, showIllustrations = true).size - 1) // -1 for end_padding
    }

    @Test
    fun `two images sharing one anchor both emit, in list order, with distinct keys`() {
        val book = illustrated.copy(
            chapters = listOf(
                illustrated.chapters[0].copy(
                    images = listOf(
                        ChapterImage(anchorParagraph = 0, path = "/img/a.png"),
                        ChapterImage(anchorParagraph = 0, path = "/img/b.png"),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf("masthead_0", "img_0_0", "img_0_1", "p_0_0", "p_0_1", "p_0_2", "end_padding"),
            emit(book, showIllustrations = true),
        )
    }
}
