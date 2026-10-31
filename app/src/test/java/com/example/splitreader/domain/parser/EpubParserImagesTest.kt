package com.example.splitreader.domain.parser

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Images the parser used to lose (found on a device with Gutenberg's illustrated "Alice", #28885):
 * every image in a chapter file without an h1–h3 (that edition has none), every image before a
 * heading, and whole files holding only images (plates, cover/title pages).
 *
 * Image-only files deliberately do NOT become chapters: that would renumber the chapters of every
 * book with an image cover page, and saved positions/bookmarks/excerpts address chapters by index.
 * Their images go to the start of the next text chapter (the end of the previous one at the end).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpubParserImagesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private fun html(body: String) =
        """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body>$body</body></html>"""

    private fun p(n: Int, tag: String) = (1..n).joinToString("") { "<p>$tag paragraph $it with enough words to count.</p>" }
    private fun img(name: String) = """<div class="figright"><img src="$name" alt="x"/><span class="caption">Caption $name</span></div>"""

    /** Writes an EPUB whose spine is [files] in order; images a.png..d.png are always present. */
    private fun epub(vararg files: String): Uri {
        val f = File(tmp.root, "b${System.nanoTime()}.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            val mime = "application/epub+zip".toByteArray()
            z.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().also { it.update(mime) }.value
            }); z.write(mime); z.closeEntry()
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            files.forEachIndexed { i, body -> put("OEBPS/c$i.xhtml", html(body).toByteArray()) }
            listOf("a", "b", "c", "d").forEach { put("OEBPS/$it.png", png + it.toByteArray()) }
            val manifest = files.indices.joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" } +
                listOf("a", "b", "c", "d").joinToString("") { """<item id="$it" href="$it.png" media-type="image/png"/>""" }
            val spine = files.indices.joinToString("") { """<itemref idref="c$it"/>""" }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>T</dc:title><dc:language>en</dc:language></metadata><manifest>$manifest</manifest><spine>$spine</spine></package>""".toByteArray())
        }
        return Uri.fromFile(f)
    }

    private suspend fun parse(uri: Uri) = EpubParser().parse(uri, ApplicationProvider.getApplicationContext())

    @Test
    fun `images in a chapter without a heading are kept, anchored where they appear`() = runTest {
        val book = parse(epub(p(2, "A") + img("a.png") + p(2, "B")))
        assertEquals(1, book.chapters.size)
        assertEquals(listOf(2), book.chapters[0].images.map { it.anchorParagraph })
    }

    @Test
    fun `an image-only file between chapters goes to the start of the next chapter, not a new chapter`() = runTest {
        val book = parse(epub("<h1>One</h1>" + p(3, "A"), img("a.png") + img("b.png"), "<h1>Three</h1>" + p(3, "C")))
        assertEquals("no chapter added for the plates", 2, book.chapters.size)
        assertEquals(listOf(0, 0), book.chapters[1].images.map { it.anchorParagraph })
        assertTrue(book.chapters[0].images.isEmpty())
    }

    @Test
    fun `an image cover page does not renumber the chapters`() = runTest {
        val book = parse(epub(img("a.png"), "<h1>One</h1>" + p(2, "A"), "<h1>Two</h1>" + p(2, "B")))
        assertEquals(2, book.chapters.size)
        assertEquals("One", book.chapters[0].title)
        assertEquals(listOf(0), book.chapters[0].images.map { it.anchorParagraph })
    }

    @Test
    fun `an image-only file at the end goes to the end of the last chapter`() = runTest {
        val book = parse(epub("<h1>One</h1>" + p(3, "A"), img("c.png")))
        assertEquals(1, book.chapters.size)
        assertEquals(listOf(3), book.chapters[0].images.map { it.anchorParagraph })
    }

    @Test
    fun `an image before the heading opens its chapter`() = runTest {
        val book = parse(epub(img("d.png") + "<h1>One</h1>" + p(2, "A")))
        assertEquals(1, book.chapters.size)
        assertEquals(listOf(0), book.chapters[0].images.map { it.anchorParagraph })
    }
}
