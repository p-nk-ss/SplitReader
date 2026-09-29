package com.example.splitreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

private fun book(vararg paragraphCounts: Int) = Book(
    title = "T", author = "A", filePath = "/b",
    chapters = paragraphCounts.mapIndexed { i, n ->
        Chapter(index = i, title = "C$i", paragraphs = List(n) { "p$it" })
    },
)

class ReadingPositionTest {

    @Test
    fun `ordinal counts every paragraph of the chapters before it`() {
        val b = book(3, 0, 2)
        assertEquals(0, b.paragraphOrdinal(ReadingPosition(0, 0)))
        assertEquals(2, b.paragraphOrdinal(ReadingPosition(0, 2)))
        assertEquals(4, b.paragraphOrdinal(ReadingPosition(2, 1))) // 3 + 0 + 1
    }

    @Test
    fun `a position inside the book is returned unchanged, offset included`() {
        val p = ReadingPosition(2, 1, offset = 40)
        assertSame(p, p.coercedTo(book(3, 0, 2)))
    }

    @Test
    fun `a position past the book's end clamps to the last paragraph and drops the offset`() {
        assertEquals(ReadingPosition(2, 1, 0), ReadingPosition(9, 9, offset = 40).coercedTo(book(3, 0, 2)))
        assertEquals(ReadingPosition(0, 2, 0), ReadingPosition(0, 7, offset = 40).coercedTo(book(3, 0, 2)))
    }

    @Test
    fun `an empty chapter clamps to paragraph zero, and an empty book to START`() {
        assertEquals(ReadingPosition(1, 0, 0), ReadingPosition(1, 5).coercedTo(book(3, 0, 2)))
        assertEquals(ReadingPosition.START, ReadingPosition(4, 4).coercedTo(book()))
    }

    @Test
    fun `ordinal clamps an out-of-range position before counting`() {
        assertEquals(4, book(3, 0, 2).paragraphOrdinal(ReadingPosition(9, 9)))
    }
}
