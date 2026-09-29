package com.example.splitreader.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.local.AppDatabase
import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.domain.model.Bookmark
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room (in-memory) so the transaction, FK and ordering are the production ones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BookmarkRepositoryReplaceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BookmarkRepositoryImpl

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BookmarkRepositoryImpl(db.bookmarkDao())
        for (uri in listOf("/b/x", "/b/y")) {
            db.bookDao().upsert(BookEntity(uri = uri, title = uri, author = "A", coverPath = null, lastOpenedAt = 0L, chapterCount = 2))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun bm(ch: Int, p: Int, label: String? = null, createdAt: Long = 0L) =
        Bookmark(id = 0, bookUri = "/b/x", chapterIndex = ch, paragraphIndex = p, label = label, createdAt = createdAt)

    @Test
    fun `replace swaps one book's bookmarks and keeps label and createdAt`() = runTest {
        repo.add("/b/x", 0, 5)
        repo.add("/b/x", 1, 9)
        repo.add("/b/y", 0, 3)

        repo.replaceForBook("/b/x", listOf(bm(0, 4, label = "L", createdAt = 111), bm(1, 8, createdAt = 222)))

        val x = repo.listForBook("/b/x")
        assertEquals(listOf(0 to 4, 1 to 8), x.map { it.chapterIndex to it.paragraphIndex })
        assertEquals(listOf("L", null), x.map { it.label })
        assertEquals(listOf(111L, 222L), x.map { it.createdAt })
        assertEquals(listOf(0 to 3), repo.listForBook("/b/y").map { it.chapterIndex to it.paragraphIndex })
    }

    @Test
    fun `listForBook orders by chapter then paragraph`() = runTest {
        repo.add("/b/x", 1, 0)
        repo.add("/b/x", 0, 7)
        repo.add("/b/x", 0, 2)
        assertEquals(listOf(0 to 2, 0 to 7, 1 to 0), repo.listForBook("/b/x").map { it.chapterIndex to it.paragraphIndex })
    }
}
