package com.example.splitreader.domain.parser.util

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

        assertFalse(staged.stage(target) { it.writeText("half"); throw IOException("disk full") })
        staged.commit()

        assertEquals("old", target.readText())
        assertEquals(listOf("a.jpg"), tmp.root.list()!!.toList())
    }
}
