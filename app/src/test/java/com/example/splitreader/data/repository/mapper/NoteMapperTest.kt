package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMapperTest {
    @Test fun roundTrip_preservesEveryField() {
        val e = NoteEntity("u", 1, 4, "body", isHighlight = true, createdAt = 10L, updatedAt = 20L)
        assertEquals(e, e.toDomain().toEntity())
    }
}
