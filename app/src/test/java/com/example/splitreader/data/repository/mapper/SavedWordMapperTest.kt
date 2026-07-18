package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.SavedWordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedWordMapperTest {
    @Test fun roundTrip_preservesEveryField() {
        val e = SavedWordEntity(
            id = 3L, word = "w", partOfSpeech = "noun", sourceLang = "en", targetLang = "ru",
            translation = "t", bookUri = "u", bookTitle = "bt", chapterIndex = 1, paragraphIndex = 2,
            contextSnippet = "ctx", note = "n", savedAt = 99L,
        )
        assertEquals(e, e.toDomain().toEntity())
    }
}
