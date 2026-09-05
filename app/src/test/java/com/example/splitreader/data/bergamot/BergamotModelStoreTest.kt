package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.InsufficientStorageException
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflinePackCorruptException
import com.example.splitreader.domain.translator.OfflinePackDownloadException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * The store is the only code that turns a manifest entry into files on disk. Rules under test:
 * resume from where a cut-off download stopped, never leave a half pack in the final directory,
 * refuse to start without room, and treat a bad hash as "delete, retry once, then give up".
 */
class BergamotModelStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val enRu = ModelPair(Language.ENGLISH, Language.RUSSIAN)
    // Random → incompressible, so the gz body is ~10 KB and a mid-transfer cut is actually mid-transfer.
    private val modelBytes = ByteArray(10_000).also { java.util.Random(1).nextBytes(it) }
    private val vocabBytes = "vocab".toByteArray()
    private val lexBytes = "lex".toByteArray()

    private fun gz(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(bytes) } }.toByteArray()

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(sha: String = sha256(modelBytes)) = BergamotManifest(
        version = 1, baseUrl = "https://bucket",
        packs = listOf(
            ManifestPack("en", "ru", "base-memory",
                model = ManifestFile("models/m.gz", modelBytes.size.toLong(), sha),
                vocab = ManifestFile("models/v.gz"), shortlist = ManifestFile("models/l.gz")),
        ),
    )

    /** Serves gz bodies by URL; can cut a transfer after N bytes once, and records Range offsets. */
    private inner class FakeFetcher(
        var cutAfter: Long = -1,
        private val corruptModelOnce: Boolean = false,
    ) : PackFetcher {
        private val bodies = mapOf(
            "https://bucket/models/m.gz" to gz(modelBytes),
            "https://bucket/models/v.gz" to gz(vocabBytes),
            "https://bucket/models/l.gz" to gz(lexBytes),
            "https://bucket/models/t.gz" to gz("trg".toByteArray()),
        )
        private var corrupted = corruptModelOnce
        val offsets = mutableListOf<Long>()
        override suspend fun fetch(url: String, offset: Long, onChunk: suspend (ByteArray, Int) -> Unit): Long {
            offsets += offset
            var body = bodies.getValue(url)
            if (corrupted && url.endsWith("m.gz")) { corrupted = false; body = gz(modelBytes.reversedArray()) }
            var sent = 0L
            var i = offset.toInt()
            while (i < body.size) {
                val n = minOf(1024, body.size - i)
                if (cutAfter in 0..(sent + n - 1)) { cutAfter = -1; throw IOException("cut") }
                onChunk(body.copyOfRange(i, i + n), n)
                i += n; sent += n
            }
            return sent
        }
    }

    // The dispatcher must share runTest's scheduler: a bare StandardTestDispatcher() gets a private
    // scheduler that nothing advances, so the flowOn producer would never run and the test would hang.
    private fun TestScope.store(fetcher: PackFetcher, manifest: BergamotManifest = manifest(), space: Long = Long.MAX_VALUE) =
        BergamotModelStore(tmp.root, manifest, fetcher, StandardTestDispatcher(testScheduler), usableSpace = { space })

    @Test
    fun `downloads all three files, verifies hash, marks installed`() = runTest {
        val s = store(FakeFetcher())
        val progress = s.ensure(enRu).toList()
        assertTrue(s.isInstalled(enRu))
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(modelBytes.toList(), s.packDir(enRu).resolve(BergamotModelStore.MODEL).readBytes().toList())
    }

    @Test
    fun `a cut transfer is resumed from the bytes already on disk`() = runTest {
        val fetcher = FakeFetcher(cutAfter = 2048)
        val s = store(fetcher)
        runCatching { s.ensure(enRu).toList() }
        assertFalse(s.isInstalled(enRu))
        s.ensure(enRu).toList()
        assertTrue(s.isInstalled(enRu))
        assertTrue("second attempt resumed, offsets=${fetcher.offsets}", fetcher.offsets.any { it > 0 })
    }

    @Test
    fun `interrupted download leaves nothing in the final pack directory`() = runTest {
        val s = store(FakeFetcher(cutAfter = 100))
        val err = runCatching { s.ensure(enRu).toList() }.exceptionOrNull()
        assertTrue(err is OfflinePackDownloadException)
        val finalFiles = s.packDir(enRu).listFiles()?.map { it.name }?.filter { !it.endsWith(".part") } ?: emptyList()
        assertEquals(emptyList<String>(), finalFiles)
    }

    @Test
    fun `bad hash is retried once and then succeeds`() = runTest {
        val fetcher = FakeFetcher(corruptModelOnce = true)
        val s = store(fetcher)
        s.ensure(enRu).toList()
        assertTrue(s.isInstalled(enRu))
        // model, model-retry (part deleted → from 0), vocab, shortlist
        assertEquals(listOf(0L, 0L, 0L, 0L), fetcher.offsets)
    }

    @Test
    fun `persistent bad hash surfaces as corrupt after the retry`() = runTest {
        val s = store(FakeFetcher(), manifest(sha = "0".repeat(64)))
        val err = runCatching { s.ensure(enRu).toList() }.exceptionOrNull()
        assertTrue(err is OfflinePackCorruptException)
        assertFalse(s.isInstalled(enRu))
    }

    @Test
    fun `refuses to start without twice the uncompressed size free`() = runTest {
        val fetcher = FakeFetcher()
        val s = store(fetcher, space = modelBytes.size * 2L - 1)
        val err = runCatching { s.ensure(enRu).toList() }.exceptionOrNull()
        assertTrue(err is InsufficientStorageException)
        assertEquals(emptyList<Long>(), fetcher.offsets)
    }

    @Test
    fun `a pack with a target vocab downloads four files`() = runTest {
        val split = BergamotManifest(
            version = 1, baseUrl = "https://bucket",
            packs = listOf(
                ManifestPack("en", "ja", "base-memory",
                    model = ManifestFile("models/m.gz", modelBytes.size.toLong(), sha256(modelBytes)),
                    vocab = ManifestFile("models/v.gz"), targetVocab = ManifestFile("models/t.gz"),
                    shortlist = ManifestFile("models/l.gz")),
            ),
        )
        val enJa = ModelPair(Language.ENGLISH, Language.JAPANESE)
        val s = store(FakeFetcher(), split)
        s.ensure(enJa).toList()
        assertTrue(s.isInstalled(enJa))
        assertEquals("trg".toByteArray().toList(), s.packDir(enJa).resolve(BergamotModelStore.TARGET_VOCAB).readBytes().toList())
    }

    @Test
    fun `delete removes the pack and installed() reflects it`() = runTest {
        val s = store(FakeFetcher())
        s.ensure(enRu).toList()
        assertEquals(listOf(enRu), s.installed().first().map { it.pair })
        s.delete(enRu)
        assertFalse(s.isInstalled(enRu))
        assertEquals(emptyList<InstalledPackPair>(), s.installed().first().map { it.pair })
    }
}
private typealias InstalledPackPair = ModelPair
