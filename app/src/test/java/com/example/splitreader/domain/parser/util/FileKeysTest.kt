package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileKeysTest {
    @Test fun deterministic_sameInput_sameId() {
        assertEquals(stableId("content://book/1"), stableId("content://book/1"))
    }

    @Test fun differentInputs_differentIds() {
        assertNotEquals(stableId("content://book/1"), stableId("content://book/2"))
    }

    @Test fun hexAndStableLength() {
        val id = stableId("file:///data/user/0/app/files/catalog/42.epub")
        assertEquals(16, id.length)
        assertTrue("id must be lowercase hex", id.all { it in "0123456789abcdef" })
    }

    @Test fun noCollisionsAcrossRealisticKeys() {
        val keys = (1..1000).map { "content://com.android.providers/document/$it" }
        assertEquals(keys.size, keys.map { stableId(it) }.toSet().size)
    }
}
