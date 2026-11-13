package com.example.splitreader.data.repository

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.DrivePickedFile
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * `localUriFor` answers "is this picked Drive file already downloaded?" from the real folder
 * (`filesDir/drive/<fileId>[.<book ext>]`). The library gate trusts it: a wrong "yes" names a file
 * that is not a book as the book's uri.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveRepositoryImplTest {

    private lateinit var dir: File
    private lateinit var repo: DriveRepositoryImpl
    private val picked = DrivePickedFile(fileId = "abc123", name = "b.epub")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        dir = File(context.filesDir, "drive").apply { deleteRecursively(); mkdirs() }
        repo = DriveRepositoryImpl(OkHttpClient(), context) // the client is never called here
    }

    private fun onDisk(name: String, bytes: Int = 8) = File(dir, name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `a downloaded book is found, with its extension`() = runTest {
        val book = onDisk("abc123.fb2")
        assertEquals(Uri.fromFile(book), repo.localUriFor(picked))
    }

    @Test
    fun `a downloaded book saved without an extension is found`() = runTest {
        val book = onDisk("abc123")
        assertEquals(Uri.fromFile(book), repo.localUriFor(picked))
    }

    @Test
    fun `a download still in flight is not a book`() = runTest {
        onDisk("abc123.part")
        assertEquals(null, repo.localUriFor(picked))
    }

    @Test
    fun `the temp next to a finished book never wins over the book`() = runTest {
        onDisk("abc123.part")
        val book = onDisk("abc123.epub")
        assertEquals(Uri.fromFile(book), repo.localUriFor(picked))
    }

    @Test
    fun `other files' downloads are not this file`() = runTest {
        onDisk("abc1234.epub")
        onDisk("abc12.epub")
        onDisk("xabc123.epub")
        onDisk("other.abc123")
        assertEquals(null, repo.localUriFor(picked))
    }

    @Test
    fun `an empty file is not a book`() = runTest {
        onDisk("abc123.epub", bytes = 0)
        assertEquals(null, repo.localUriFor(picked))
    }

    @Test
    fun `nothing downloaded yet`() = runTest {
        dir.deleteRecursively()
        assertEquals(null, repo.localUriFor(picked))
    }
}
