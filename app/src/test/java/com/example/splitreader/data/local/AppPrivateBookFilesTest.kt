package com.example.splitreader.data.local

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.parser.util.stableId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Real files under Robolectric's filesDir, named exactly as the parsers and downloaders name them:
 * `covers/<stableId(uri)>.<ext>`, `images/<stableId(uri)>_<name>.<ext>`, their `.part-<UUID>` temps,
 * `catalog/<source>/<id>.epub`, `drive/<fileId>[.ext]`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppPrivateBookFilesTest {

    private lateinit var context: Application
    private lateinit var files: AppPrivateBookFiles

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        files = AppPrivateBookFiles(context)
    }

    private val longAgo get() = System.currentTimeMillis() - 2 * 60 * 60 * 1000L

    private fun file(dir: String, name: String): File =
        File(File(context.filesDir, dir).apply { mkdirs() }, name).apply { writeText("x") }

    private fun names(dir: String): List<String> =
        File(context.filesDir, dir).listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun `the book's cover, pictures and their temp files are deleted, another book's are kept`() = runTest {
        val uri = "content://books/x"
        val other = "content://books/y"
        val id = stableId(uri)
        val otherId = stableId(other)
        file("covers", "$id.jpg")
        file("covers", "$id.png.part-${UUID.randomUUID()}").setLastModified(longAgo)
        file("covers", "$otherId.jpg")
        file("images", "${id}_plate1.png")
        file("images", "${id}_plate2.jpg.part-${UUID.randomUUID()}").setLastModified(longAgo)
        file("images", "${otherId}_plate1.png")

        files.deleteFor(uri)

        assertEquals(listOf("$otherId.jpg"), names("covers"))
        assertEquals(listOf("${otherId}_plate1.png"), names("images"))
    }

    /**
     * A parse of this very book may be running (it was opened, then deleted from another screen): its
     * fresh temps are that parse's working files. Deleting them made its commit fail; the hourly
     * sweep (StagedFiles.sweepStale) takes them if the parse really died.
     */
    @Test
    fun `temp files a running parse may still be writing are left for the sweep`() = runTest {
        val uri = "content://books/x"
        val id = stableId(uri)
        val freshCover = "$id.png.part-${UUID.randomUUID()}"
        val freshPicture = "${id}_plate2.jpg.part-${UUID.randomUUID()}"
        file("covers", "$id.png")
        file("covers", freshCover)
        file("images", "${id}_plate2.jpg")
        file("images", freshPicture)
        // Not a temp, only named like one: a picture whose own name contains ".part-".
        file("images", "${id}_pic.part-1.jpg")

        files.deleteFor(uri)

        assertEquals(listOf(freshCover), names("covers"))
        assertEquals(listOf(freshPicture), names("images"))
    }

    @Test
    fun `a catalog download is deleted with its book`() = runTest {
        val book = file("catalog/gutenberg", "84.epub")
        val kept = file("catalog/gutenberg", "85.epub")

        files.deleteFor(Uri.fromFile(book).toString())

        assertFalse(book.exists())
        assertTrue(kept.exists())
    }

    @Test
    fun `a Drive download is deleted with its book`() = runTest {
        val book = file("drive", "1AbC.fb2")

        files.deleteFor(Uri.fromFile(book).toString())

        assertFalse(book.exists())
    }

    @Test
    fun `a file outside the app's download folders is never deleted`() = runTest {
        val outside = File(context.cacheDir, "mine.epub").apply { writeText("x") }
        val bergamot = file("bergamot", "model.bin")
        // A path that only looks like it is inside the download folder.
        val escaping = "file://${context.filesDir}/catalog/../bergamot/model.bin"

        files.deleteFor(Uri.fromFile(outside).toString())
        files.deleteFor(escaping)
        files.deleteFor(Uri.fromFile(File(context.filesDir, "catalog")).toString())

        assertTrue(outside.exists())
        assertTrue(bergamot.exists())
    }

    @Test
    fun `a picked file's persisted read grant is released`() = runTest {
        val uri = Uri.parse("content://com.android.providers.documents/document/7")
        val other = Uri.parse("content://com.android.providers.documents/document/8")
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resolver.takePersistableUriPermission(other, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        files.deleteFor(uri.toString())

        assertEquals(listOf(other), resolver.persistedUriPermissions.map { it.uri })
    }

    @Test
    fun `a book with nothing on disk and no grant deletes quietly`() = runTest {
        files.deleteFor("content://books/none")
        files.deleteFor("not a uri at all")
    }

    // ── Orphan sweep ─────────────────────────────────────────────────────────

    private fun old(dir: String, name: String): File = file(dir, name).apply { setLastModified(longAgo) }

    /**
     * A parse still running when its book is deleted commits its pictures after the delete removed
     * them: files of a book the library no longer has, which nothing would ever delete.
     */
    @Test
    fun `old covers and pictures of a book that is not in the library are swept`() = runTest {
        val gone = stableId("content://books/gone")
        old("covers", "$gone.jpg")
        old("images", "${gone}_plate1.png")
        old("images", "${gone}_plate2.jpg.part-${UUID.randomUUID()}")

        files.sweepOrphans(listOf("content://books/x"))

        assertEquals(emptyList<String>(), names("covers"))
        assertEquals(emptyList<String>(), names("images"))
    }

    @Test
    fun `the sweep keeps the files of a book in the library, however old`() = runTest {
        val uri = "content://books/x"
        val id = stableId(uri)
        old("covers", "$id.jpg")
        old("images", "${id}_plate1.png")
        old("covers", "${stableId("content://books/gone")}.jpg")

        files.sweepOrphans(listOf(uri))

        assertEquals(listOf("$id.jpg"), names("covers"))
        assertEquals(listOf("${id}_plate1.png"), names("images"))
    }

    /** A first import writes its pictures before its library row: fresh files with no book yet. */
    @Test
    fun `the sweep keeps fresh files of a book that is not in the library yet`() = runTest {
        val importing = stableId("content://books/importing")
        file("covers", "$importing.jpg")
        file("images", "${importing}_plate1.png")
        file("images", "${importing}_plate2.png.part-${UUID.randomUUID()}")

        files.sweepOrphans(emptyList())

        assertEquals(1, names("covers").size)
        assertEquals(2, names("images").size)
    }

    /**
     * An import that could not rewrite one picture shows the old file that was there (StagedFiles.stage):
     * while anything of the book is fresh, nothing of it is an orphan.
     */
    @Test
    fun `one fresh file keeps the book's old files too`() = runTest {
        val importing = stableId("content://books/importing")
        old("covers", "$importing.jpg")
        old("images", "${importing}_plate1.png")
        file("images", "${importing}_plate2.png")

        files.sweepOrphans(emptyList())

        assertEquals(listOf("$importing.jpg"), names("covers"))
        assertEquals(listOf("${importing}_plate1.png", "${importing}_plate2.png"), names("images"))
    }

    @Test
    fun `the sweep leaves alone what is not named like a book's file, and folders`() = runTest {
        old("covers", "-1234567.jpg")          // a pre-stableId cover name
        old("covers", "notes.txt")
        old("images", "0123456789abcdef.png")  // no "_": not a picture name
        File(context.filesDir, "images/${stableId("content://books/gone")}_dir").mkdirs()
        old("catalog", "${stableId("content://books/gone")}.epub")

        files.sweepOrphans(emptyList())

        assertEquals(listOf("-1234567.jpg", "notes.txt"), names("covers"))
        assertEquals(listOf("${stableId("content://books/gone")}_dir", "0123456789abcdef.png").sorted(), names("images"))
        assertEquals(1, names("catalog").size)
    }
}
