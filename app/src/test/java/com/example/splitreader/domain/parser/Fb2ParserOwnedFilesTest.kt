package com.example.splitreader.domain.parser

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.parser.util.stableId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * FB2 books are re-parsed on every open, and a parse writes its cover/pictures to paths that are
 * deterministic per book URI -- the SAME files an owned book's cover and chapter images point at.
 * A failed or cancelled re-parse must therefore never delete (or half-overwrite) those files; only
 * a successful parse replaces them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Fb2ParserOwnedFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private val oldBytes = "OLD-PICTURE".toByteArray()

    // base64 of "newcover" / "newimage" (no magic bytes -> both sniff as jpg)
    private val newCover = "bmV3Y292ZXI="
    private val newImage = "bmV3aW1hZ2U="

    private fun fb2(tail: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <FictionBook>
          <description><title-info>
            <book-title>T</book-title>
            <coverpage><image l:href="#cover.jpg"/></coverpage>
          </title-info></description>
          <body><section><title><p>Ch</p></title><p>Hello</p><image l:href="#img1"/></section></body>
          <binary id="cover.jpg" content-type="image/jpeg">$newCover</binary>
          <binary id="img1" content-type="image/jpeg">$newImage</binary>
    """.trimIndent() + tail

    private fun bookFile(content: String): Uri {
        val f = File(tmp.root, "b${System.nanoTime()}.fb2")
        f.writeText(content)
        return Uri.fromFile(f)
    }

    private fun coverFile(uri: Uri) = File(File(context.filesDir, "covers"), "${stableId(uri.toString())}.jpg")
    private fun imageFile(uri: Uri) = File(File(context.filesDir, "images"), "${stableId(uri.toString())}_img1.jpg")

    private fun ownBook(uri: Uri) {
        listOf(coverFile(uri), imageFile(uri)).forEach {
            it.parentFile!!.mkdirs()
            it.writeBytes(oldBytes)
        }
    }

    private fun tempFilesOf(uri: Uri): List<File> {
        val hash = stableId(uri.toString())
        return listOf("covers", "images").flatMap { d ->
            File(context.filesDir, d).listFiles()?.filter { it.name.startsWith(hash) && it.name.contains(".part") }.orEmpty()
        }
    }

    private fun assertOwnedFilesIntact(uri: Uri) {
        assertTrue("owned cover must survive a failed re-parse", coverFile(uri).exists())
        assertTrue("owned picture must survive a failed re-parse", imageFile(uri).exists())
        assertArrayEquals("owned cover keeps its original bytes", oldBytes, coverFile(uri).readBytes())
        assertArrayEquals("owned picture keeps its original bytes", oldBytes, imageFile(uri).readBytes())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }

    @Test
    fun `a re-parse that fails after the pictures keeps the owned book's cover and pictures`() = runTest {
        // Truncated file (e.g. a broken re-download): the binaries parse, then the XML breaks.
        val uri = bookFile(fb2("\n  <binary id=\"x\">AAAA</wrong>\n</FictionBook>\n"))
        ownBook(uri)

        try {
            Fb2Parser().parse(uri, context)
            fail("expected the truncated FB2 to fail")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        assertOwnedFilesIntact(uri)
    }

    @Test
    fun `a re-parse cancelled after the pictures keeps the owned book's cover and pictures`() = runTest {
        val head = fb2("\n").toByteArray()
        val padding = ("<!-- " + "p".repeat(200_000) + " -->\n</FictionBook>\n").toByteArray()
        val uri = Uri.parse("content://test/cancel${System.nanoTime()}.fb2")
        ownBook(uri)
        lateinit var job: Job
        // Once every byte up to the end of the binaries has been handed out, the next read cancels
        // the parse (like leaving the reader while a large FB2 is still parsing).
        val stream = object : InputStream() {
            private val all = ByteArrayInputStream(head + padding)
            private var served = 0
            override fun read(): Int = ByteArray(1).let { b -> if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xFF }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served >= head.size) job.cancel()
                return all.read(b, off, len).also { if (it > 0) served += it }
            }
        }
        shadowOf(context.contentResolver).registerInputStream(uri, stream)

        var caught: Throwable? = null
        job = launch {
            try {
                Fb2Parser().parse(uri, context)
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        job.join()

        assertTrue("CancellationException must propagate, was $caught", caught is CancellationException)
        assertOwnedFilesIntact(uri)
    }

    @Test
    fun `a successful re-parse replaces the files at the same paths and leaves no temp files`() = runTest {
        val uri = bookFile(fb2("\n</FictionBook>\n"))
        ownBook(uri)

        val book = Fb2Parser().parse(uri, context)

        assertEquals(coverFile(uri).absolutePath, book.coverPath)
        val image = book.chapters.single().images.single()
        assertEquals(imageFile(uri).absolutePath, image.path)
        assertArrayEquals("newcover".toByteArray(), coverFile(uri).readBytes())
        assertArrayEquals("newimage".toByteArray(), imageFile(uri).readBytes())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }

    @Test
    fun `a picture whose temp write fails keeps the owned file and the book still opens`() = runTest {
        val uri = bookFile(fb2("\n</FictionBook>\n"))
        ownBook(uri)
        // A directory squatting on the temp path makes the temp write throw (like a disk-full write).
        val tempName = "blocked.part-test"
        listOf(coverFile(uri), imageFile(uri)).forEach { File(File(it.parentFile, tempName), "keep").apply { parentFile!!.mkdirs(); writeText("x") } }

        val book = try {
            Fb2Parser(tempNameFor = { tempName }).parse(uri, context)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AssertionError("the book must still open when a picture can't be written, but: $e")
        }

        assertEquals(1, book.chapters.size)
        listOf(coverFile(uri), imageFile(uri)).forEach {
            assertTrue("owned ${it.name} must still be a file with its original bytes", it.isFile && it.readBytes().contentEquals(oldBytes))
        }
    }

    @Test
    fun `a parse sweeps stale temp files but keeps fresh ones and symlinks`() = runTest {
        val images = File(context.filesDir, "images").apply { mkdirs() }
        val covers = File(context.filesDir, "covers").apply { mkdirs() }
        val hourAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
        val staleImage = File(images, "s${System.nanoTime()}_a.jpg.part-1").apply { writeText("x"); setLastModified(hourAgo) }
        val staleCover = File(covers, "s${System.nanoTime()}.jpg.part-2").apply { writeText("x"); setLastModified(hourAgo) }
        val fresh = File(images, "f${System.nanoTime()}_a.jpg.part-3").apply { writeText("x") }
        val outside = File(tmp.root, "outside.jpg").apply { writeText("x"); setLastModified(hourAgo) }
        val link = File(images, "l${System.nanoTime()}_a.jpg.part-4")
        java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())

        Fb2Parser().parse(bookFile(fb2("\n</FictionBook>\n")), context)

        assertFalse("stale image temp swept", staleImage.exists())
        assertFalse("stale cover temp swept", staleCover.exists())
        assertTrue("fresh temp (a running parse's) kept", fresh.exists())
        assertTrue("symlink left alone", java.nio.file.Files.isSymbolicLink(link.toPath()))
        assertTrue("symlink target untouched", outside.exists())
    }

    @Test
    fun `a first import that fails leaves no files behind`() = runTest {
        val uri = bookFile(fb2("\n  <binary id=\"x\">AAAA</wrong>\n</FictionBook>\n"))

        try {
            Fb2Parser().parse(uri, context)
            fail("expected the truncated FB2 to fail")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        assertFalse("no cover from a failed first import", coverFile(uri).exists())
        assertFalse("no picture from a failed first import", imageFile(uri).exists())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }
}
