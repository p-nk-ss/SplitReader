package com.example.splitreader.domain.parser

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A parse that writes a cover file and then fails later (here: an FB2 whose <body> has no
 * sections, so "No chapters found" is thrown right after the cover was already decoded and saved)
 * must not leave that file behind in filesDir/covers -- nothing else ever deletes it (the book
 * never reaches the library), so every failed re-attempt on the same file would add one more
 * orphan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Fb2ParserOrphanCleanupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun chapterlessFb2WithCover(): Uri {
        val f = File(tmp.root, "b${System.nanoTime()}.fb2")
        f.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook>
              <description>
                <title-info>
                  <book-title>T</book-title>
                  <coverpage><image l:href="#cover.jpg"/></coverpage>
                </title-info>
              </description>
              <body></body>
              <binary id="cover.jpg" content-type="image/jpeg">Y292ZXI=</binary>
            </FictionBook>
            """.trimIndent(),
        )
        return Uri.fromFile(f)
    }

    @Test
    fun `a cover written before a later failure is deleted, not left orphaned`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val coversDir = File(context.filesDir, "covers")

        try {
            Fb2Parser().parse(chapterlessFb2WithCover(), context)
            fail("expected parse to throw: no chapters")
        } catch (_: IllegalStateException) {
            // expected -- the FB2 has no sections at all
        }

        assertFalse(
            "cover file from the failed parse must not survive it",
            coversDir.exists() && coversDir.listFiles()?.isNotEmpty() == true,
        )
    }
}
