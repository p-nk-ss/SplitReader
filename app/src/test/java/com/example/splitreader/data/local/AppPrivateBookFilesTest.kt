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
        file("covers", "$id.png.part-${UUID.randomUUID()}")
        file("covers", "$otherId.jpg")
        file("images", "${id}_plate1.png")
        file("images", "${id}_plate2.jpg.part-${UUID.randomUUID()}")
        file("images", "${otherId}_plate1.png")

        files.deleteFor(uri)

        assertEquals(listOf("$otherId.jpg"), names("covers"))
        assertEquals(listOf("${otherId}_plate1.png"), names("images"))
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
}
