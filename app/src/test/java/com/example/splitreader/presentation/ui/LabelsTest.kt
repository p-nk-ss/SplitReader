package com.example.splitreader.presentation.ui

import com.example.splitreader.presentation.home.BookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small label rules found wrong on a device: "1 saved words", "51%" vs "50 / 50", "Unread" for a read book. */
class LabelsTest {

    @Test
    fun `singular for one, plural otherwise`() {
        assertEquals("word", plural(1, "word"))
        assertEquals("words", plural(0, "word"))
        assertEquals("volumes", plural(2, "volume"))
    }

    @Test
    fun `split labels round half up and always add to 100`() {
        assertEquals("50 / 50", splitLabel(0.5f))
        assertEquals("51 / 49", splitLabel(0.505f))
        assertEquals("30 / 70", splitLabel(0.3f))
    }

    private fun item(progress: Float, chapter: Int = 0) =
        BookItem(uri = "u", title = "t", author = "a", coverPath = null, chapterCount = 1, lastChapterIndex = chapter, progress = progress)

    @Test
    fun `a one-chapter book read past its first paragraph counts as started`() {
        assertTrue(item(progress = 0.2f).isStarted)
        assertFalse(item(progress = 0f).isStarted)
        assertTrue("old data without saved progress still counts by chapter", item(progress = 0f, chapter = 2).isStarted)
    }
}
