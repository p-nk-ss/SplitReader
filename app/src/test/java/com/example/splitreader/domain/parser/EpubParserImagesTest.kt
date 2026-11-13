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
    private fun epub(vararg files: String, cover: String? = null, svg: Boolean = false): Uri {
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
            if (svg) put("OEBPS/t.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray())
            val manifest = files.indices.joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" } +
                listOf("a", "b", "c", "d").joinToString("") {
                    val prop = if (cover == "$it.png") " properties=\"cover-image\"" else ""
                    """<item id="$it" href="$it.png" media-type="image/png"$prop/>"""
                } + (if (svg) """<item id="t" href="t.svg" media-type="image/svg+xml"/>""" else "")
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

    @Test
    fun `the cover page does not repeat the library cover in chapter 1`() = runTest {
        val book = parse(epub(img("a.png"), "<h1>One</h1>" + p(2, "A"), cover = "a.png"))
        assertEquals(1, book.chapters.size)
        assertTrue(book.chapters[0].images.isEmpty())
    }

    @Test
    fun `an SVG title page is skipped, not shown as an empty slot`() = runTest {
        val book = parse(epub(img("t.svg"), "<h1>One</h1>" + p(2, "A"), svg = true))
        assertTrue(book.chapters[0].images.isEmpty())
    }

    @Test
    fun `a drop-cap image inside text stays a letter, not an illustration`() = runTest {
        val book = parse(epub("""<p><img src="a.png" alt="C"/>hapter text that starts with a drop cap and goes on.</p>""" + p(1, "B")))
        assertTrue(book.chapters[0].images.isEmpty())
        assertTrue(book.chapters[0].paragraphs[0], book.chapters[0].paragraphs[0].startsWith("Chapter text"))
    }

    @Test
    fun `a book of only images gets one chapter holding them`() = runTest {
        val book = parse(epub(img("a.png"), img("b.png")))
        assertEquals(1, book.chapters.size)
        assertEquals(2, book.chapters[0].images.size)
    }

    @Test
    fun `only images the old parser produced are marked as legacy layout`() = runTest {
        val book = parse(epub(
            p(1, "A") + img("a.png") + p(1, "B"),                       // heading-less: new
            img("c.png") + "<h1>Two</h1>" + p(1, "C") + img("b.png") + p(1, "D"), // before heading: new; after: old
        ))
        assertEquals(listOf(false), book.chapters[0].images.map { it.inLegacyLayout })
        assertEquals(listOf(false, true), book.chapters[1].images.map { it.inLegacyLayout })
    }

    // ── One picture used many times (an ornament under every chapter heading) ────────────────────

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun chapter(n: Int, picture: String) = "<h1>Chapter $n</h1>" + p(2, "C$n") + img(picture)

    @Test
    fun `a picture used in every chapter is staged once, and every chapter shows it`() = runTest {
        val uri = epub(chapter(1, "a.png"), chapter(2, "a.png"), chapter(3, "a.png"))
        val images = File(context.filesDir, "images")
        var temps = -1
        val hash = com.example.splitreader.domain.parser.util.stableId(uri.toString())

        val book = EpubParser().apply {
            afterPictures = { temps = images.listFiles().orEmpty().count { it.name.startsWith(hash) && ".part-" in it.name } }
        }.parse(uri, context)

        assertEquals("temp copies of the one picture", 1, temps)
        val paths = book.chapters.map { it.images.single().path }
        assertEquals(3, paths.size)
        assertEquals(1, paths.toSet().size)
        org.junit.Assert.assertArrayEquals(png + "a".toByteArray(), File(paths[0]).readBytes())
    }

    @Test
    fun `a picture that is also the cover is saved as both`() = runTest {
        val book = EpubParser().parse(epub(chapter(1, "a.png"), chapter(2, "a.png"), cover = "a.png"), context)

        org.junit.Assert.assertArrayEquals(png + "a".toByteArray(), File(book.coverPath!!).readBytes())
        book.chapters.forEach { org.junit.Assert.assertArrayEquals(png + "a".toByteArray(), File(it.images.single().path).readBytes()) }
    }

    @Test
    fun `a picture used after a heading and again on a page of its own is in both places`() = runTest {
        // The second use is not in the legacy layout: its file is looked at again (SVG check) after
        // the first use has already taken it out of the scratch folder.
        val book = EpubParser().parse(epub(chapter(1, "a.png"), img("a.png"), chapter(2, "b.png")), context)

        assertEquals(2, book.chapters.size)
        val a = book.chapters[0].images.single().path
        assertEquals(listOf(a), book.chapters[1].images.filterNot { it.inLegacyLayout }.map { it.path })
        org.junit.Assert.assertArrayEquals(png + "a".toByteArray(), File(a).readBytes())
    }

    @Test
    fun `staging a scratch file it may take moves it instead of copying`() {
        val source = File(tmp.root, "e0").apply { writeBytes(png) }
        val staged = com.example.splitreader.domain.parser.util.StagedFiles()

        val path = ImageStore.stage(context, source, "moved_${System.nanoTime()}", staged, move = true)

        assertEquals("the scratch file was moved, not copied", false, source.exists())
        staged.commit()
        org.junit.Assert.assertArrayEquals(png, File(path!!).readBytes())
    }

    @Test
    fun `staging a file it may not take leaves it where it is`() {
        val source = File(tmp.root, "e1").apply { writeBytes(png) }
        val staged = com.example.splitreader.domain.parser.util.StagedFiles()

        val path = ImageStore.stage(context, source, "copied_${System.nanoTime()}", staged)

        assertEquals(true, source.exists())
        staged.commit()
        org.junit.Assert.assertArrayEquals(png, File(path!!).readBytes())
    }
}
