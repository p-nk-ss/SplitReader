package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

/** The engine reads protected prefixes ("Mrs", "Dr", …) from a file path; the lists ship as assets. */
class SplitPrefixesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val assets = mapOf("bergamot/ssplit/nonbreaking_prefix.en" to "Mr\nMrs\n")
    private val opened = mutableListOf<String>()
    private fun prefixes() = SplitPrefixes(tmp.root) { path ->
        opened += path
        assets[path]?.let { ByteArrayInputStream(it.toByteArray()) }
    }

    @Test
    fun `a shipped list is copied out once and its file returned`() {
        val p = prefixes()
        val file = p.fileFor(Language.ENGLISH)!!
        assertEquals("Mr\nMrs\n", file.readText())
        assertEquals(file, p.fileFor(Language.ENGLISH))
        assertEquals(1, opened.size)
    }

    @Test
    fun `a language without a list gets none`() {
        assertNull(prefixes().fileFor(Language.JAPANESE))
    }

    @Test
    fun `a copy left empty by an interrupted write is replaced`() {
        tmp.newFile("nonbreaking_prefix.en")   // zero bytes
        assertEquals("Mr\nMrs\n", prefixes().fileFor(Language.ENGLISH)!!.readText())
    }
}
