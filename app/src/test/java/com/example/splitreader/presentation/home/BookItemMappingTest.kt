package com.example.splitreader.presentation.home

import com.example.splitreader.domain.model.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Library card's progress and Reading/Unread state, from what the reader saved. */
class BookItemMappingTest {

    private val book = LibraryBook(
        uri = "content://b", title = "T", author = "A", coverPath = null,
        lastOpenedAt = 0L, chapterCount = 10, synopsis = null,
    )

    @Test
    fun `a finished book reads 100 percent whatever was saved`() {
        assertEquals(1f, bookItemOf(book, finished = true, chapter = 3, savedProgress = 0.4f, excerpt = "x").progress)
    }

    @Test
    fun `the saved paragraph progress is shown`() {
        assertEquals(0.42f, bookItemOf(book, finished = false, chapter = 3, savedProgress = 0.42f, excerpt = "x").progress)
    }

    @Test
    fun `a book read before progress was saved falls back to the chapter ratio`() {
        assertEquals(0.3f, bookItemOf(book, finished = false, chapter = 3, savedProgress = null, excerpt = null).progress)
        val noChapters = book.copy(chapterCount = 0)
        assertEquals(0f, bookItemOf(noChapters, finished = false, chapter = 0, savedProgress = null, excerpt = null).progress)
    }

    @Test
    fun `scrolled into the first paragraph, the book is started, as its excerpt says`() {
        // Progress is 0 inside paragraph 0, but the reader saved the excerpt once it left the start.
        assertTrue(bookItemOf(book, finished = false, chapter = 0, savedProgress = 0f, excerpt = "It was").isStarted)
    }

    @Test
    fun `a book only opened is not started`() {
        assertFalse(bookItemOf(book, finished = false, chapter = 0, savedProgress = 0f, excerpt = null).isStarted)
    }
}
