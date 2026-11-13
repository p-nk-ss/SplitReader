package com.example.splitreader.domain.parser

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.parser.util.stableId
import com.example.splitreader.fakes.FakeCrashReporter
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
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * EPUBs are re-parsed on every open, and a parse writes its cover/pictures to paths that are
 * deterministic per book URI -- the SAME files an owned book's cover and chapter images point at.
 * A failed or cancelled re-parse must never touch those files; only a successful parse replaces them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpubParserOwnedFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private val oldBytes = "OLD-PICTURE".toByteArray()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val newCover = png + "newcover".toByteArray()
    private val newImage = png + "newimage".toByteArray()

    /** An EPUB with cover `cover.png` and one chapter showing `pic.png`. */
    private fun epub(bulk: Int = 0): Uri {
        val f = File(tmp.root, "b${System.nanoTime()}.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            val mime = "application/epub+zip".toByteArray()
            z.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size
                crc = CRC32().also { it.update(mime) }.value
            }); z.write(mime); z.closeEntry()
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            put("OEBPS/c0.xhtml", """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body><h1>One</h1><p>Hello there, a paragraph with enough words.</p><img src="pic.png" alt="x"/><p>More words in a second paragraph.</p></body></html>""".toByteArray())
            put("OEBPS/cover.png", newCover)
            put("OEBPS/pic.png", newImage)
            // Incompressible filler pictures, so the file is much longer than one read.
            repeat(bulk) { put("OEBPS/bulk$it.png", kotlin.random.Random(it).nextBytes(16 * 1024)) }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>T</dc:title><dc:language>en</dc:language></metadata><manifest><item id="c0" href="c0.xhtml" media-type="application/xhtml+xml"/><item id="cv" href="cover.png" media-type="image/png" properties="cover-image"/><item id="p" href="pic.png" media-type="image/png"/></manifest><spine><itemref idref="c0"/></spine></package>""".toByteArray())
        }
        return Uri.fromFile(f)
    }

    private fun coverFile(uri: Uri) = File(File(context.filesDir, "covers"), "${stableId(uri.toString())}.png")
    private fun imageFile(uri: Uri) = File(File(context.filesDir, "images"), "${stableId(uri.toString())}_pic.png.png")

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
        assertArrayEquals("owned cover keeps its original bytes", oldBytes, coverFile(uri).takeIf { it.isFile }?.readBytes())
        assertArrayEquals("owned picture keeps its original bytes", oldBytes, imageFile(uri).takeIf { it.isFile }?.readBytes())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }

    private fun failingParser() = EpubParser().apply { afterPictures = { throw IOException("broken later in the book") } }

    @Test
    fun `the expected final paths are the ones a parse writes`() = runTest {
        val uri = epub()
        val book = EpubParser().parse(uri, context)
        assertEquals(coverFile(uri).absolutePath, book.coverPath)
        assertEquals(imageFile(uri).absolutePath, book.chapters.single().images.single().path)
    }

    @Test
    fun `a re-parse that fails after the pictures keeps the owned book's cover and pictures`() = runTest {
        val uri = epub()
        ownBook(uri)

        try {
            failingParser().parse(uri, context)
            fail("expected the parse to fail")
        } catch (e: IOException) {
            // expected
        }

        assertOwnedFilesIntact(uri)
    }

    @Test
    fun `a re-parse cancelled after the pictures keeps the owned book's cover and pictures`() = runTest {
        val uri = epub()
        ownBook(uri)
        lateinit var job: Job
        var caught: Throwable? = null
        job = launch {
            try {
                EpubParser().apply { afterPictures = { job.cancel() } }.parse(uri, context)
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        job.join()

        assertOwnedFilesIntact(uri)
        assertTrue("CancellationException must propagate, was $caught", caught is CancellationException)
    }

    @Test
    fun `a picture whose temp write fails keeps the owned file and the book still opens`() = runTest {
        val uri = epub()
        ownBook(uri)
        // A directory squatting on the temp path makes the temp write throw (like a disk-full write).
        val tempName = "blocked.part-test"
        listOf(coverFile(uri), imageFile(uri)).forEach { File(File(it.parentFile, tempName), "keep").apply { parentFile!!.mkdirs(); writeText("x") } }

        val crashes = FakeCrashReporter()
        val book = try {
            EpubParser(crashes).apply { tempNameFor = { tempName } }.parse(uri, context)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AssertionError("the book must still open when a picture can't be written, but: $e")
        }

        assertEquals(1, book.chapters.size)
        listOf(coverFile(uri), imageFile(uri)).forEach {
            assertTrue("owned ${it.name} must still be a file with its original bytes", it.isFile && it.readBytes().contentEquals(oldBytes))
        }
        listOf(coverFile(uri), imageFile(uri)).forEach { File(it.parentFile, tempName).deleteRecursively() }
        // Dropped pictures used to vanish without a trace. One non-fatal per parse, however many were lost.
        assertEquals(
            listOf("2 pictures of an EPUB book could not be saved"),
            crashes.recorded.map { it.second },
        )
    }

    /** One picture that cannot be moved into place at the end used to fail the whole open, every time. */
    @Test
    fun `a picture that cannot be moved into place is dropped and the book still opens`() = runTest {
        val uri = epub()
        // A non-empty directory squatting on the picture's final path: the move throws.
        File(imageFile(uri), "keep").apply { parentFile!!.mkdirs(); writeText("x") }
        val crashes = FakeCrashReporter()

        val book = try {
            EpubParser(crashes).parse(uri, context)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AssertionError("the book must still open when a picture can't be moved into place, but: $e")
        }
        imageFile(uri).deleteRecursively()

        assertEquals("the lost picture must not be referenced", emptyList<String>(), book.chapters.single().images.map { it.path })
        assertEquals(coverFile(uri).absolutePath, book.coverPath)
        assertArrayEquals(newCover, coverFile(uri).readBytes())
        assertEquals(emptyList<File>(), tempFilesOf(uri))
        assertEquals(listOf("1 picture of an EPUB book could not be saved"), crashes.recorded.map { it.second })
    }

    @Test
    fun `a book whose pictures are all saved reports nothing`() = runTest {
        val crashes = FakeCrashReporter()
        EpubParser(crashes).parse(epub(), context)
        assertEquals(emptyList<String?>(), crashes.recorded.map { it.second })
    }

    /**
     * The zip is read in one loop before any chapter is looked at -- up to hundreds of MB. It had no
     * cancellation check: closing the reader mid-open kept reading (and spilling pictures to the
     * cache) to the end of the file.
     */
    @Test
    fun `a re-parse cancelled while the file is being read stops reading and keeps the owned book's files`() = runTest {
        val file = File(epub(bulk = 64).path!!)
        val uri = Uri.parse("content://books/cancelled-${System.nanoTime()}.epub")
        ownBook(uri)
        lateinit var job: Job
        var cancelled = false
        var readAfterCancel = 0L
        val stream = object : java.io.FilterInputStream(file.inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val r = super.read(b, off, len)
                if (cancelled) { if (r > 0) readAfterCancel += r } else { cancelled = true; job.cancel() }
                return r
            }
        }
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, stream)
        var caught: Throwable? = null
        job = launch {
            try {
                EpubParser().parse(uri, context)
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        job.join()

        assertTrue("CancellationException must propagate, was $caught", caught is CancellationException)
        assertTrue(
            "the parse kept reading after it was cancelled: $readAfterCancel of ${file.length()} bytes",
            readAfterCancel < 64 * 1024,
        )
        assertOwnedFilesIntact(uri)
        assertEquals("no scratch folder left", emptyList<String>(), context.cacheDir.list().orEmpty().filter { it.startsWith("epub-") })
    }

    @Test
    fun `a successful re-parse replaces the files at the same paths and leaves no temp files`() = runTest {
        val uri = epub()
        ownBook(uri)

        val book = EpubParser().parse(uri, context)

        assertEquals(coverFile(uri).absolutePath, book.coverPath)
        assertEquals(imageFile(uri).absolutePath, book.chapters.single().images.single().path)
        assertArrayEquals(newCover, coverFile(uri).readBytes())
        assertArrayEquals(newImage, imageFile(uri).readBytes())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }

    @Test
    fun `a first import that fails leaves no files behind`() = runTest {
        val uri = epub()

        try {
            failingParser().parse(uri, context)
            fail("expected the parse to fail")
        } catch (e: IOException) {
            // expected
        }

        assertFalse("no cover from a failed first import", coverFile(uri).exists())
        assertFalse("no picture from a failed first import", imageFile(uri).exists())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }
}
