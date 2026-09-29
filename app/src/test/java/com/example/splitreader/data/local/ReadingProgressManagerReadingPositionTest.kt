package com.example.splitreader.data.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.repository.LegacyProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real SharedPreferences under Robolectric: the plausible defects are key names (a position written
 * to one key and read from another; migrating one book deleting another book's keys), which a fake
 * cannot observe. Legacy keys are seeded raw, exactly as the pre-2026-09 `saveProgress` wrote them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingProgressManagerReadingPositionTest {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var manager: ReadingProgressManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        manager = ReadingProgressManager(context)
    }

    private fun seedLegacy(uri: String, chapter: Int, item: Int, offset: Int) {
        prefs.edit()
            .putInt("last_chapter_$uri", chapter)
            .putInt("last_scroll_${uri}_$chapter", item)
            .putInt("last_scroll_offset_${uri}_$chapter", offset)
            .commit()
    }

    @Test
    fun `an unread book is at START`() {
        assertEquals(ReadingPosition.START, manager.getReadingPosition("/b/x"))
    }

    @Test
    fun `a Reading position round-trips per book`() {
        manager.saveReadingPosition("/b/x", ReadingPosition(3, 7, 42))
        manager.saveReadingPosition("/b/y", ReadingPosition(1, 2, 0))
        assertEquals(ReadingPosition(3, 7, 42), manager.getReadingPosition("/b/x"))
        assertEquals(ReadingPosition(1, 2, 0), manager.getReadingPosition("/b/y"))
        assertEquals("/b/y", manager.getLastBookUri())
    }

    @Test
    fun `the chapter shares the legacy last_chapter key so Home keeps unmigrated books' progress`() {
        seedLegacy("/b/x", chapter = 4, item = 9, offset = 0)
        assertEquals(4, manager.getReadingPosition("/b/x").chapter)
    }

    @Test
    fun `legacyProgress reads the last chapter's item and offset, null when never saved`() {
        assertNull(manager.legacyProgress("/b/x"))
        seedLegacy("/b/x", chapter = 2, item = 5, offset = 30)
        assertEquals(LegacyProgress(2, 5, 30), manager.legacyProgress("/b/x"))
    }

    @Test
    fun `completing a migration writes the position, sets the flag and drops every legacy key`() {
        seedLegacy("/b/x", chapter = 2, item = 5, offset = 30)
        prefs.edit().putInt("last_scroll_/b/x_0", 3).putInt("last_scroll_offset_/b/x_0", 1).commit()
        assertFalse(manager.isReadingPositionMigrated("/b/x"))

        manager.completeReadingPositionMigration("/b/x", ReadingPosition(2, 4, 30))

        assertTrue(manager.isReadingPositionMigrated("/b/x"))
        assertEquals(ReadingPosition(2, 4, 30), manager.getReadingPosition("/b/x"))
        assertEquals(
            emptyList<String>(),
            prefs.all.keys.filter { it.startsWith("last_scroll_") }.sorted(),
        )
        assertNull(manager.legacyProgress("/b/x"))
    }

    @Test
    fun `migrating a book leaves a book whose uri extends it untouched`() {
        seedLegacy("/b/a", chapter = 0, item = 2, offset = 0)
        seedLegacy("/b/a_1", chapter = 1, item = 6, offset = 8)

        manager.completeReadingPositionMigration("/b/a", ReadingPosition(0, 1, 0))

        assertEquals(LegacyProgress(1, 6, 8), manager.legacyProgress("/b/a_1"))
        assertFalse(manager.isReadingPositionMigrated("/b/a_1"))
    }

    @Test
    fun `a migration with no legacy progress sets the flag and writes no position`() {
        manager.completeReadingPositionMigration("/b/x", null)
        assertTrue(manager.isReadingPositionMigrated("/b/x"))
        assertEquals(ReadingPosition.START, manager.getReadingPosition("/b/x"))
    }

    @Test
    fun `clearProgress forgets the Reading position`() {
        manager.saveReadingPosition("/b/x", ReadingPosition(3, 7, 42))
        manager.clearProgress("/b/x")
        assertEquals(ReadingPosition.START, manager.getReadingPosition("/b/x"))
    }
}
