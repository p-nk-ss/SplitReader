package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.repository.BookmarkRepository
import com.example.splitreader.domain.repository.LegacyProgress
import com.example.splitreader.domain.repository.LegacyReadingPositionStore
import com.example.splitreader.domain.usecase.MigrateLegacyReadingPositionsUseCase.Companion.legacyItemToPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Chapter 0, legacy layout with illustrations on:
 *   0 masthead · 1 p0 · 2 img(anchor 1) · 3 p1 · 4 p2 · 5 img(anchor 3 = trailing)
 * With illustrations off:  0 masthead · 1 p0 · 2 p1 · 3 p2
 */
private val ch0 = Chapter(
    index = 0, title = "One", paragraphs = listOf("a", "b", "c"),
    images = listOf(ChapterImage(anchorParagraph = 1, path = "/i1"), ChapterImage(anchorParagraph = 3, path = "/i2")),
)
private val book = Book(
    title = "T", author = "A", filePath = "/b/x",
    chapters = listOf(ch0, Chapter(index = 1, title = "Plates", paragraphs = emptyList())),
)

private class FakeLegacyStore(var progress: LegacyProgress? = null, val log: MutableList<String>) :
    LegacyReadingPositionStore {
    var migrated = false
    var written: ReadingPosition? = null
    override fun isReadingPositionMigrated(bookUri: String) = migrated
    override fun legacyProgress(bookUri: String) = progress
    override fun completeReadingPositionMigration(bookUri: String, position: ReadingPosition?) {
        log += "prefs"; written = position; migrated = true; progress = null
    }
}

private class FakeBookmarks(var stored: List<Bookmark>, val log: MutableList<String>) : BookmarkRepository {
    var replaceCalls = 0
    override fun observeForBook(uri: String): Flow<List<Bookmark>> = emptyFlow()
    override suspend fun add(bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String?) = Unit
    override suspend fun remove(bookUri: String, chapterIndex: Int, paragraphIndex: Int) = Unit
    override suspend fun toggle(bookUri: String, chapterIndex: Int, paragraphIndex: Int) = Unit
    override suspend fun listForBook(bookUri: String) = stored.sortedWith(compareBy({ it.chapterIndex }, { it.paragraphIndex }))
    override suspend fun replaceForBook(bookUri: String, bookmarks: List<Bookmark>) {
        log += "bookmarks"; replaceCalls++; stored = bookmarks
    }
}

private fun bm(id: Long, ch: Int, item: Int, createdAt: Long) =
    Bookmark(id = id, bookUri = "/b/x", chapterIndex = ch, paragraphIndex = item, label = null, createdAt = createdAt)

class MigrateLegacyReadingPositionsUseCaseTest {

    // Split into one @Test per legacy item so each assertion is individually observable when
    // broken (a single combined test's assertEquals calls stop at the first failure, masking the
    // rest). Values are unchanged from the original combined test.

    @Test
    fun `legacy item 0, the masthead, maps to the first paragraph below`() {
        assertEquals(ReadingPosition(0, 0, 0), legacyItemToPosition(ch0, 0, 0, 9, showIllustrations = true))
    }

    @Test
    fun `legacy item 1, paragraph 0, keeps its offset`() {
        assertEquals(ReadingPosition(0, 0, 9), legacyItemToPosition(ch0, 0, 1, 9, showIllustrations = true))
    }

    @Test
    fun `legacy item 2, the image above paragraph 1, maps to paragraph 1`() {
        assertEquals(ReadingPosition(0, 1, 0), legacyItemToPosition(ch0, 0, 2, 9, showIllustrations = true))
    }

    @Test
    fun `legacy item 3 maps to paragraph 1, not paragraph 2`() {
        // legacy `item-1` would say p2
        assertEquals(ReadingPosition(0, 1, 9), legacyItemToPosition(ch0, 0, 3, 9, showIllustrations = true))
    }

    @Test
    fun `legacy item 5, the trailing image, maps to the last paragraph`() {
        assertEquals(ReadingPosition(0, 2, 0), legacyItemToPosition(ch0, 0, 5, 9, showIllustrations = true))
    }

    @Test
    fun `legacy item 99, past the chapter, maps to the last paragraph`() {
        assertEquals(ReadingPosition(0, 2, 0), legacyItemToPosition(ch0, 0, 99, 9, showIllustrations = true))
    }

    @Test
    fun `legacy items map to paragraphs with illustrations off`() {
        assertEquals(ReadingPosition(0, 2, 4), legacyItemToPosition(ch0, 0, 3, 4, showIllustrations = false))
    }

    @Test
    fun `an empty chapter maps to its paragraph zero`() {
        assertEquals(ReadingPosition(1, 0, 0), legacyItemToPosition(book.chapters[1], 1, 3, 4, showIllustrations = true))
    }

    @Test
    fun `progress is converted and written once, before bookmarks`() = runTest {
        val log = mutableListOf<String>()
        val legacy = FakeLegacyStore(LegacyProgress(chapter = 0, itemIndex = 3, offset = 12), log)
        val marks = FakeBookmarks(listOf(bm(1, 0, 4, 10)), log)

        MigrateLegacyReadingPositionsUseCase(legacy, marks)(book, showIllustrations = true)

        assertEquals(ReadingPosition(0, 1, 12), legacy.written)
        assertEquals(listOf("prefs", "bookmarks"), log)
    }

    @Test
    fun `no legacy progress still marks the book migrated`() = runTest {
        val log = mutableListOf<String>()
        val legacy = FakeLegacyStore(null, log)
        MigrateLegacyReadingPositionsUseCase(legacy, FakeBookmarks(emptyList(), log))(book, true)
        assertEquals(null, legacy.written)
        assertEquals(listOf("prefs"), log)
    }

    @Test
    fun `bookmarks convert and collisions keep the earliest`() = runTest {
        val log = mutableListOf<String>()
        val marks = FakeBookmarks(
            listOf(
                bm(1, 0, 0, createdAt = 100), // masthead -> p0
                bm(2, 0, 1, createdAt = 200), // p0      -> p0 (collides, later: dropped)
                bm(3, 0, 3, createdAt = 300), // p1
                bm(4, 7, 5, createdAt = 400), // chapter no longer exists: kept as is
            ),
            log,
        )
        MigrateLegacyReadingPositionsUseCase(FakeLegacyStore(null, log), marks)(book, true)

        assertEquals(
            listOf(Triple(0, 0, 100L), Triple(0, 1, 300L), Triple(7, 5, 400L)),
            marks.stored.map { Triple(it.chapterIndex, it.paragraphIndex, it.createdAt) },
        )
    }

    @Test
    fun `a second open does not convert bookmarks again`() = runTest {
        val log = mutableListOf<String>()
        val legacy = FakeLegacyStore(null, log)
        val marks = FakeBookmarks(listOf(bm(1, 0, 3, 10)), log) // item 3 -> p1
        val migrate = MigrateLegacyReadingPositionsUseCase(legacy, marks)

        migrate(book, true)
        migrate(book, true)

        assertEquals(listOf(0 to 1), marks.stored.map { it.chapterIndex to it.paragraphIndex })
        assertEquals(1, marks.replaceCalls)
    }

    @Test
    fun `no bookmarks, or none that move, means no bookmark write`() = runTest {
        val empty = FakeBookmarks(emptyList(), mutableListOf())
        MigrateLegacyReadingPositionsUseCase(FakeLegacyStore(null, mutableListOf()), empty)(book, true)
        assertEquals(0, empty.replaceCalls)

        // Only a bookmark in a chapter the book no longer has: nothing converts, nothing is rewritten.
        val stale = FakeBookmarks(listOf(bm(1, 7, 5, 10)), mutableListOf())
        MigrateLegacyReadingPositionsUseCase(FakeLegacyStore(null, mutableListOf()), stale)(book, true)
        assertEquals(0, stale.replaceCalls)
    }
}
