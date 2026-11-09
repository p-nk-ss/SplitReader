package com.example.splitreader.domain.parser

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.parser.util.BookTooLargeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * The size budget must not refuse an ordinary illustrated catalog book. Gutenberg's Count of Monte
 * Cristo (.epub.images, what the Catalog downloads) is 82.4 MiB of entries, the largest 0.25 MiB;
 * counting every picture against heap/4 refused it on a 256 MiB heap, and Treasure Island (47.4 MiB)
 * under ~190 MiB — v25 opened both. Pictures are streamed to files, so only text counts against the
 * heap; the whole book still has the 300 MB zip-bomb bound.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpubParserBudgetTest {
    @get:Rule val tmp = TemporaryFolder()

    private val heap = 4L * 1024 * 1024 // memory budget heap/4 = 1 MiB

    private fun epub(chapterBytes: Int, images: List<ByteArray>): Uri {
        val f = File(tmp.root, "b${System.nanoTime()}.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            val imgs = images.indices.joinToString("") { """<div><img src="p$it.png" alt="Plate $it"/></div>""" }
            val filler = "<p>" + "word ".repeat(chapterBytes / 5) + "</p>"
            put("OEBPS/c0.xhtml", """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body><h1>One</h1><p>Start.</p>$imgs$filler</body></html>""".toByteArray())
            images.forEachIndexed { i, b -> put("OEBPS/p$i.png", b) }
            val manifest = """<item id="c0" href="c0.xhtml" media-type="application/xhtml+xml"/>""" +
                images.indices.joinToString("") { """<item id="p$it" href="p$it.png" media-type="image/png"/>""" }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>T</dc:title><dc:language>en</dc:language></metadata><manifest>$manifest</manifest><spine><itemref idref="c0"/></spine></package>""".toByteArray())
        }
        return Uri.fromFile(f)
    }

    private fun png(size: Int, seed: Int) =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + Random(seed).nextBytes(size)

    private suspend fun parse(uri: Uri) =
        EpubParser().apply { heapLimit = { heap } }.parse(uri, ApplicationProvider.getApplicationContext())

    @Test
    fun `pictures that together exceed the memory budget still open, every picture saved`() = runTest {
        val images = (0 until 12).map { png(200 * 1024, it) } // 2.4 MiB of pictures > 1 MiB budget
        val book = parse(epub(chapterBytes = 10_000, images = images))

        assertEquals(1, book.chapters.size)
        val saved = book.chapters[0].images.map { File(it.path) }
        assertEquals(12, saved.size)
        saved.forEachIndexed { i, f -> assertArrayEquals(images[i], f.readBytes()) }
    }

    @Test
    fun `text over the memory budget is still refused`() = runTest {
        val uri = epub(chapterBytes = 2 * 1024 * 1024, images = emptyList())
        assertThrows(BookTooLargeException::class.java) {
            kotlinx.coroutines.runBlocking { parse(uri) }
        }
    }
}
