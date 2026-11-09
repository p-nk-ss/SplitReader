package com.example.splitreader.domain.parser

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A parse that died mid-import (process death) never reaches its own `finally` and leaves
 * `cacheDir/epub-<uuid>` behind forever. The next EPUB parse must sweep stale ones away, but never
 * touch one young enough that a concurrent parse could still be using it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpubParserSpillSweepTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun minimalEpub(): Uri {
        val f = File(tmp.root, "b${System.nanoTime()}.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            put("OEBPS/c0.xhtml", """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body><h1>One</h1><p>Start.</p></body></html>""".toByteArray())
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">x</dc:identifier><dc:title>T</dc:title><dc:language>en</dc:language></metadata><manifest><item id="c0" href="c0.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c0"/></spine></package>""".toByteArray())
        }
        return Uri.fromFile(f)
    }

    @Test
    fun `a stale epub- folder from a previous parse that never cleaned up is swept, a fresh one is kept`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val cacheDir = context.cacheDir
        val now = 10_000_000_000L
        val hour = 60 * 60 * 1000L

        val stale = File(cacheDir, "epub-abandoned").apply { mkdirs() }
        File(stale, "e0").writeText("leftover")
        stale.setLastModified(now - hour - 1)

        val fresh = File(cacheDir, "epub-concurrent").apply { mkdirs() }
        File(fresh, "e0").writeText("still being written")
        fresh.setLastModified(now - hour + 1)

        val parser = EpubParser().apply { nowMillis = { now } }
        parser.parse(minimalEpub(), context)

        assertFalse("stale epub- folder should have been swept", stale.exists())
        assertTrue("fresh epub- folder must survive (a concurrent parse could still own it)", fresh.exists())
    }
}
