package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookMapperTest {
    @Test fun toDomain_copiesEveryField() {
        val e = BookEntity("uri://x", "Title", "Author", "cover/p", 123L, 7, "syn")
        val d = e.toDomain()
        assertEquals(e.uri, d.uri)
        assertEquals(e.title, d.title)
        assertEquals(e.author, d.author)
        assertEquals(e.coverPath, d.coverPath)
        assertEquals(e.lastOpenedAt, d.lastOpenedAt)
        assertEquals(e.chapterCount, d.chapterCount)
        assertEquals(e.synopsis, d.synopsis)
    }
}
