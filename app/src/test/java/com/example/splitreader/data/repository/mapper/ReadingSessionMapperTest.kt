package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.DailyMinutesRow
import com.example.splitreader.data.local.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionMapperTest {
    @Test fun session_roundTrip_preservesEveryField() {
        val e = ReadingSessionEntity(
            id = 7L, bookUri = "u", bookTitle = "bt", sourceLang = "en",
            startedAt = 100L, endedAt = 200L, durationSeconds = 100, paragraphsRead = 4,
        )
        assertEquals(e, e.toDomain().toEntity())
    }

    @Test fun statsRow_mapsToDomainFields() {
        val d = DailyMinutesRow("2026-07-18", 42).toDomain()
        assertEquals("2026-07-18", d.day)
        assertEquals(42, d.minutes)
    }
}
