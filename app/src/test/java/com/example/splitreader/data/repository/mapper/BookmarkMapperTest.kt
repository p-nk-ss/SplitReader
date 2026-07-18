package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookmarkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkMapperTest {
    @Test fun toDomain_copiesEveryField() {
        val e = BookmarkEntity(id = 5L, bookUri = "u", chapterIndex = 2, paragraphIndex = 9, label = "lbl", createdAt = 111L)
        val d = e.toDomain()
        assertEquals(e.id, d.id)
        assertEquals(e.bookUri, d.bookUri)
        assertEquals(e.chapterIndex, d.chapterIndex)
        assertEquals(e.paragraphIndex, d.paragraphIndex)
        assertEquals(e.label, d.label)
        assertEquals(e.createdAt, d.createdAt)
    }
}
