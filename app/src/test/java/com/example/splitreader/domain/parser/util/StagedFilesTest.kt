package com.example.splitreader.domain.parser.util

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class StagedFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun owned(name: String) = File(tmp.root, name).apply { writeText("old") }

    @Test
    fun `nothing reaches the target before commit, then it is replaced and no temp is left`() {
        val target = owned("a.jpg")
        val staged = StagedFiles()

        assertTrue(staged.stage(target) { it.writeText("new") })
        assertEquals("old", target.readText())

        staged.commit()
        assertEquals("new", target.readText())
        assertEquals(listOf("a.jpg"), tmp.root.list()!!.toList())
    }

    @Test
    fun `rollback leaves the target untouched and removes the temps`() {
        val target = owned("a.jpg")
        val staged = StagedFiles()
        staged.stage(target) { it.writeText("new") }

        staged.rollback()

        assertEquals("old", target.readText())
        assertEquals(listOf("a.jpg"), tmp.root.list()!!.toList())
    }

    @Test
    fun `a failed write is not staged, its temp is deleted and commit keeps the target`() {
        val target = owned("a.jpg")
        val staged = StagedFiles()

        // The owned file is intact, so its path is still good for the book (true), though nothing was staged.
        assertTrue(staged.stage(target) { it.writeText("half"); throw IOException("disk full") })
        assertEquals(emptySet<String>(), staged.commit())

        assertEquals("old", target.readText())
        assertEquals(listOf("a.jpg"), tmp.root.list()!!.toList())
    }

    @Test
    fun `a failed write with no file at the target gives no path to show`() {
        val target = File(tmp.root, "a.jpg")
        val staged = StagedFiles()

        assertFalse(staged.stage(target) { it.writeText("half"); throw IOException("disk full") })
        staged.commit()

        assertEquals(emptyList<String>(), tmp.root.list()!!.toList())
    }

    // ── commit: one file that cannot be moved must not fail the parse ────────────────────────────

    /** A non-empty directory squatting on the target: Files.move(REPLACE_EXISTING) throws. */
    private fun blockedTarget(name: String) = File(tmp.root, name).apply { File(this, "keep").apply { parentFile!!.mkdirs(); writeText("x") } }

    @Test
    fun `a file that cannot be moved does not stop the others, its temp is deleted and its path is returned`() {
        val blocked = blockedTarget("a.jpg")
        val fine = File(tmp.root, "b.jpg")
        val staged = StagedFiles()
        staged.stage(blocked) { it.writeText("new-a") }
        staged.stage(fine) { it.writeText("new-b") }

        val missing = staged.commit()

        assertEquals(setOf(blocked.absolutePath), missing)
        assertEquals("new-b", fine.readText())
        assertEquals(listOf("a.jpg", "b.jpg"), tmp.root.list()!!.sorted())
    }

    @Test
    fun `a failed move over a file that was already there keeps that file and its path`() {
        val target = owned("a.jpg")
        val staged = StagedFiles { "a.jpg.part-gone" }
        staged.stage(target) { it.writeText("new") }
        File(tmp.root, "a.jpg.part-gone").delete() // the move will fail: NoSuchFileException

        val missing = staged.commit()

        assertEquals(emptySet<String>(), missing)
        assertEquals("old", target.readText())
    }

    @Test
    fun `a failed move is reported as a lost picture`() {
        val staged = StagedFiles()
        staged.stage(blockedTarget("a.jpg")) { it.writeText("new") }
        staged.commit()
        val crashes = com.example.splitreader.fakes.FakeCrashReporter()

        staged.reportLost(crashes, "an EPUB")

        assertEquals(listOf("1 picture of an EPUB book could not be saved"), crashes.recorded.map { it.second })
    }

    @Test
    fun `commit of a book drops the cover and the pictures whose files are missing, and only those`() {
        val lostPicture = blockedTarget("p1.jpg")
        val lostCover = blockedTarget("c.jpg")
        val kept = File(tmp.root, "p2.jpg")
        val staged = StagedFiles()
        listOf(lostPicture, lostCover, kept).forEach { f -> staged.stage(f) { it.writeText("new") } }
        val book = Book(
            "T", "A",
            listOf(
                Chapter(0, "One", listOf("a", "b"), images = listOf(ChapterImage(0, lostPicture.absolutePath), ChapterImage(1, kept.absolutePath))),
                Chapter(1, "Two", listOf("c")),
            ),
            "content://b", coverPath = lostCover.absolutePath,
        )

        val committed = staged.commit(book)

        assertEquals(null, committed.coverPath)
        assertEquals(listOf(ChapterImage(1, kept.absolutePath)), committed.chapters[0].images)
        assertEquals(book.chapters.map { it.paragraphs }, committed.chapters.map { it.paragraphs })
    }
}
