package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [sweepStaleSpillDirs] clears `cacheDir/epub-*` scratch folders a parse that died mid-import
 * (process death) never got to delete in its own `finally`. A fake clock keeps the one-hour
 * boundary exact, instead of racing real file-system timestamp precision.
 */
class SpillDirsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val hour = 60 * 60 * 1000L

    private fun dirAgedAt(name: String, ageMillis: Long, now: Long): File {
        val dir = File(tmp.root, name).apply { mkdirs() }
        File(dir, "e0").writeText("x")
        dir.setLastModified(now - ageMillis)
        return dir
    }

    @Test fun staleDirOlderThanMaxAge_isDeleted() {
        val now = 10_000_000L
        val stale = dirAgedAt("epub-abandoned", ageMillis = hour + 1, now = now)

        sweepStaleSpillDirs(tmp.root, "epub-", maxAgeMillis = hour, now = now)

        assertFalse(stale.exists())
    }

    @Test fun freshDirUnderMaxAge_isKept() {
        val now = 10_000_000L
        val fresh = dirAgedAt("epub-running", ageMillis = hour - 1, now = now)

        sweepStaleSpillDirs(tmp.root, "epub-", maxAgeMillis = hour, now = now)

        assertTrue(fresh.exists())
    }

    @Test fun currentParsesOwnDirIsNeverSwept_evenIfItLooksOld() {
        val now = 10_000_000L
        val current = dirAgedAt("epub-current", ageMillis = hour + 1, now = now)

        sweepStaleSpillDirs(tmp.root, "epub-", maxAgeMillis = hour, keep = current, now = now)

        assertTrue(current.exists())
    }

    @Test fun nonMatchingPrefixIsNeverSwept() {
        val now = 10_000_000L
        val other = dirAgedAt("fb2-abandoned", ageMillis = hour + 1, now = now)

        sweepStaleSpillDirs(tmp.root, "epub-", maxAgeMillis = hour, now = now)

        assertTrue(other.exists())
    }

    @Test fun aPlainFileSharingThePrefixIsNeverSwept() {
        val now = 10_000_000L
        val file = File(tmp.root, "epub-not-a-dir").apply { writeText("x") }
        file.setLastModified(now - hour - 1)

        sweepStaleSpillDirs(tmp.root, "epub-", maxAgeMillis = hour, now = now)

        assertTrue(file.exists())
    }

    @Test fun aSymlinkSharingThePrefixIsNeverFollowedOrDeleted() {
        // isDirectory() follows a symlink, so a stale-looking "epub-*" symlink into some unrelated
        // directory used to pass the isDirectory+prefix check and then deleteRecursively() walked
        // into (and deleted the contents of) whatever it points at -- not a scratch folder this
        // parser ever owned.
        val now = 10_000_000L
        val hour = 60 * 60 * 1000L
        val target = File(tmp.root, "real-target").apply { mkdirs() }
        val important = File(target, "important").apply { writeText("do not delete") }
        val link = File(tmp.root, "epub-link")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (_: Exception) {
            org.junit.Assume.assumeTrue("symlinks unsupported in this test JVM/filesystem", false)
            return
        }
        link.setLastModified(now - hour - 1)

        sweepStaleSpillDirs(tmp.root, "epub-", maxAgeMillis = hour, now = now)

        assertTrue("the symlink itself must survive", link.exists())
        assertTrue("the real target's contents must survive", important.exists())
    }
}
