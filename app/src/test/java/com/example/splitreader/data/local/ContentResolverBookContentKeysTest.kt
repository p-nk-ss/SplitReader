package com.example.splitreader.data.local

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The key is the SHA-256 of the whole file, in lower-case hex; a file that cannot be read has none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ContentResolverBookContentKeysTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val keys = ContentResolverBookContentKeys(context)

    @Test
    fun `the key is the SHA-256 of the file's content`() = runTest {
        val file = File(context.filesDir, "hello.epub").apply { writeText("hello") }
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            keys.keyOf(Uri.fromFile(file).toString()),
        )
    }

    @Test
    fun `a file larger than one read buffer is hashed whole`() = runTest {
        val a = File(context.filesDir, "a.epub").apply { writeBytes(ByteArray(200_000) { 1 }) }
        val b = File(context.filesDir, "b.epub").apply { writeBytes(ByteArray(200_000) { 1 } + byteArrayOf(2)) }
        val ka = keys.keyOf(Uri.fromFile(a).toString())
        val kb = keys.keyOf(Uri.fromFile(b).toString())
        assertEquals(64, ka?.length)
        assert(ka != kb)
    }

    @Test
    fun `a file that cannot be read has no key`() = runTest {
        assertNull(keys.keyOf(Uri.fromFile(File(context.filesDir, "gone.epub")).toString()))
        assertNull(keys.keyOf("content://nobody/doc/1"))
    }
}
