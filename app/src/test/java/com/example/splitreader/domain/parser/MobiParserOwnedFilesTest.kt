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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * MOBIs are re-parsed on every open, and the cover goes to `filesDir/covers/<stableId(uri)>.<ext>`
 * -- the SAME file an owned book's cover is. A failed or cancelled re-parse must never touch it;
 * only a successful parse replaces it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MobiParserOwnedFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private val oldBytes = "OLD-PICTURE".toByteArray()
    private val newCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + "newcover".toByteArray()

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
    private fun u32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    /** A classic uncompressed MOBI 6: record 0 (PalmDOC + MOBI + EXTH with cover offset 0), one text record, the cover image. */
    private fun mobi(): Uri {
        val mobiHeaderLength = 0x74
        val rec0 = ByteArrayOutputStream().apply {
            // PalmDOC header: compression 1, unused, text length, 1 text record, record size, encryption 0, unused
            write(u16(1)); write(u16(0)); write(u32(0)); write(u16(1)); write(u16(4096)); write(u16(0)); write(u16(0))
            val mobi = ByteArray(mobiHeaderLength)
            "MOBI".toByteArray().copyInto(mobi, 0)
            u32(mobiHeaderLength).copyInto(mobi, 4)
            u32(65001).copyInto(mobi, 12)        // text encoding (rec0 offset 28)
            u32(6).copyInto(mobi, 20)            // file version (rec0 offset 36)
            u32(2).copyInto(mobi, 108 - 16)      // first image record
            u32(0x40).copyInto(mobi, 0x80 - 16)  // EXTH present
            write(mobi)
            // EXTH: one record, type 201 (cover offset) = 0
            write("EXTH".toByteArray()); write(u32(12 + 12)); write(u32(1))
            write(u32(201)); write(u32(12)); write(u32(0))
        }.toByteArray()
        val text = "<h1>One</h1><p>Hello there, a paragraph with enough words.</p>".toByteArray()
        val records = listOf(rec0, text, newCover)
        val out = ByteArrayOutputStream()
        val header = ByteArray(78)
        "test".toByteArray().copyInto(header, 0)
        "BOOKMOBI".toByteArray().copyInto(header, 60)
        u16(records.size).copyInto(header, 76)
        out.write(header)
        var offset = 78 + records.size * 8 + 2
        records.forEachIndexed { i, r -> out.write(u32(offset)); out.write(u32(i)); offset += r.size }
        out.write(u16(0))
        records.forEach { out.write(it) }
        val f = File(tmp.root, "b${System.nanoTime()}.mobi")
        f.writeBytes(out.toByteArray())
        return Uri.fromFile(f)
    }

    private fun coverFile(uri: Uri) = File(File(context.filesDir, "covers"), "${stableId(uri.toString())}.jpg")

    private fun ownBook(uri: Uri) {
        coverFile(uri).apply { parentFile!!.mkdirs(); writeBytes(oldBytes) }
    }

    private fun tempFilesOf(uri: Uri): List<File> {
        val hash = stableId(uri.toString())
        return File(context.filesDir, "covers").listFiles()?.filter { it.name.startsWith(hash) && it.name.contains(".part") }.orEmpty()
    }

    private fun assertOwnedCoverIntact(uri: Uri) {
        assertArrayEquals("owned cover keeps its original bytes", oldBytes, coverFile(uri).takeIf { it.isFile }?.readBytes())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }

    private fun failingParser() = MobiParser().apply { afterPictures = { throw IOException("broken later in the book") } }

    @Test
    fun `the fixture parses with its cover at the expected path`() = runTest {
        val uri = mobi()
        val book = MobiParser().parse(uri, context)
        assertEquals(1, book.chapters.size)
        assertEquals(coverFile(uri).absolutePath, book.coverPath)
    }

    @Test
    fun `a re-parse that fails after the cover keeps the owned book's cover`() = runTest {
        val uri = mobi()
        ownBook(uri)

        try {
            failingParser().parse(uri, context)
            fail("expected the parse to fail")
        } catch (e: IOException) {
            // expected
        }

        assertOwnedCoverIntact(uri)
    }

    @Test
    fun `a re-parse cancelled after the cover keeps the owned book's cover`() = runTest {
        val uri = mobi()
        ownBook(uri)
        lateinit var job: Job
        var caught: Throwable? = null
        job = launch {
            try {
                MobiParser().apply { afterPictures = { job.cancel() } }.parse(uri, context)
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        job.join()

        assertOwnedCoverIntact(uri)
        assertTrue("CancellationException must propagate, was $caught", caught is CancellationException)
    }

    @Test
    fun `a cover whose temp write fails keeps the owned file and the book still opens`() = runTest {
        val uri = mobi()
        ownBook(uri)
        val tempName = "blocked.part-test"
        val blocker = File(coverFile(uri).parentFile, tempName)
        File(blocker, "keep").apply { parentFile!!.mkdirs(); writeText("x") }

        val crashes = FakeCrashReporter()
        val book = try {
            MobiParser(crashes).apply { tempNameFor = { tempName } }.parse(uri, context)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw AssertionError("the book must still open when the cover can't be written, but: $e")
        }

        assertEquals(1, book.chapters.size)
        assertTrue("owned cover must still be a file with its original bytes", coverFile(uri).isFile && coverFile(uri).readBytes().contentEquals(oldBytes))
        blocker.deleteRecursively()
        assertEquals("the owned cover is still the book's cover", coverFile(uri).absolutePath, book.coverPath)
        assertEquals(listOf("1 picture of a MOBI book could not be saved"), crashes.recorded.map { it.second })
    }

    @Test
    fun `a successful re-parse replaces the cover at the same path and leaves no temp files`() = runTest {
        val uri = mobi()
        ownBook(uri)

        val book = MobiParser().parse(uri, context)

        assertEquals(coverFile(uri).absolutePath, book.coverPath)
        assertArrayEquals(newCover, coverFile(uri).readBytes())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }

    @Test
    fun `a first import that fails leaves no files behind`() = runTest {
        val uri = mobi()

        try {
            failingParser().parse(uri, context)
            fail("expected the parse to fail")
        } catch (e: IOException) {
            // expected
        }

        assertFalse("no cover from a failed first import", coverFile(uri).exists())
        assertEquals("no temp files left", emptyList<File>(), tempFilesOf(uri))
    }
}
