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
    fun `forgetting a book removes every key stored for it`() {
        seedLegacy("/b/x", chapter = 2, item = 5, offset = 30)
        manager.saveReadingPosition("/b/x", ReadingPosition(3, 7, 42))
        manager.saveProgress("/b/x", 0.25f)
        manager.saveExcerpt("/b/x", "excerpt")
        manager.markFinished("/b/x")
        manager.saveSourceLanguage("/b/x", com.example.splitreader.domain.model.Language.GERMAN)
        manager.completeReadingPositionMigration("/b/x", ReadingPosition(3, 7, 42))
        seedLegacy("/b/x", chapter = 3, item = 5, offset = 30)

        prefs.edit().putString("last_book_uri", "/b/x").commit() // written by versions up to 27

        manager.forgetBook("/b/x")

        assertEquals(emptyList<String>(), prefs.all.filter { (k, v) -> "/b/x" in k || v == "/b/x" }.keys.sorted())
        assertEquals(ReadingPosition.START, manager.getReadingPosition("/b/x"))
        assertNull(manager.getProgress("/b/x"))
        assertFalse(manager.isFinished("/b/x"))
        // Not "migrated": a re-import has no legacy data, and its migration then just sets the flag.
        assertFalse(manager.isReadingPositionMigrated("/b/x"))
        assertNull(manager.legacyProgress("/b/x"))
    }

    @Test
    fun `forgetting a book leaves a book whose uri extends it untouched`() {
        for (uri in listOf("/b/a", "/b/a_1")) {
            seedLegacy(uri, chapter = 1, item = 6, offset = 8)
            manager.saveProgress(uri, 0.5f)
            manager.markFinished(uri)
        }

        manager.forgetBook("/b/a")

        assertEquals(LegacyProgress(1, 6, 8), manager.legacyProgress("/b/a_1"))
        assertEquals(0.5f, manager.getProgress("/b/a_1")!!, 0f)
        assertTrue(manager.isFinished("/b/a_1"))
    }

    @Test
    fun `progress is null until saved and round-trips`() {
        assertNull(manager.getProgress("/b/x"))
        manager.saveProgress("/b/x", 0.25f)
        assertEquals(0.25f, manager.getProgress("/b/x")!!, 0f)
    }

    @Test
    fun `copying a book carries every per-book key to the new uri and leaves the old ones as they were`() {
        seedLegacy("/b/x", chapter = 3, item = 6, offset = 8)
        prefs.edit().putBoolean("position_v2_/b/x", true).commit()
        manager.saveReadingPosition("/b/x", ReadingPosition(3, 7, 42))
        manager.saveProgress("/b/x", 0.5f)
        manager.saveExcerpt("/b/x", "Call me Ishmael")
        manager.markFinished("/b/x")
        manager.saveSourceLanguage("/b/x", com.example.splitreader.domain.model.Language.GERMAN)
        manager.saveProgress("/b/y", 0.9f) // the new uri's own stale value is replaced
        manager.saveProgress("/b/x_1", 0.1f) // a uri that extends the old one is not touched

        val before = prefs.all.filterKeys { "/b/x" in it && "/b/x_1" !in it }

        manager.copyBook("/b/x", "/b/y")

        assertEquals("the old uri keeps everything until forgetBook", before, prefs.all.filterKeys { "/b/x" in it && "/b/x_1" !in it })
        assertEquals(ReadingPosition(3, 7, 42), manager.getReadingPosition("/b/y"))
        assertEquals(0.5f, manager.getProgress("/b/y")!!, 0f)
        assertEquals("Call me Ishmael", manager.getExcerpt("/b/y"))
        assertTrue(manager.isFinished("/b/y"))
        assertEquals(com.example.splitreader.domain.model.Language.GERMAN, manager.getSourceLanguage("/b/y"))
        assertTrue(manager.isReadingPositionMigrated("/b/y"))
        assertEquals(LegacyProgress(3, 6, 8), manager.legacyProgress("/b/y"))
        assertEquals(0.1f, manager.getProgress("/b/x_1")!!, 0f)
    }

    @Test
    fun `copying a book clears what the new uri had that the old one did not`() {
        manager.markFinished("/b/y")
        manager.saveProgress("/b/x", 0.2f)

        manager.copyBook("/b/x", "/b/y")

        assertFalse(manager.isFinished("/b/y"))
        assertEquals(0.2f, manager.getProgress("/b/y")!!, 0f)
    }
}
