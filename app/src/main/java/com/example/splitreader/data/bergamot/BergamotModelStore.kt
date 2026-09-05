package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.InstalledPack
import com.example.splitreader.domain.translator.InsufficientStorageException
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineModelStore
import com.example.splitreader.domain.translator.OfflinePackCorruptException
import com.example.splitreader.domain.translator.OfflinePackDownloadException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Files on disk for Offline HQ packs: `<root>/<src>-<tgt>/{model.bin,vocab.spm,lex.s2t.bin,installed.v1}`.
 * The `.gz` is downloaded to `<name>.gz.part` (resumable with Range), then gunzipped to `<name>.tmp`,
 * hashed (model only — the manifest carries no hash for vocab/shortlist), and renamed into place.
 * The pack counts as installed only when the marker exists; the marker is written last.
 *
 * Everything in a pack directory belongs to one manifest version. [MARKER] holds the version it was
 * installed under and [STAMP] — written first into a fresh directory — records the version a
 * half-finished directory is being built for. A pack whose marker names a different version is not
 * installed, and a directory whose stamp does not match is wiped before downloading: after an app
 * update that changes a pack's URL or hash, a v1 `model.bin` or a v1 `.part` must never be resumed
 * into, or accepted as, a v2 pack.
 *
 * **Timeouts.** This store does not watch a silent socket. [stallTimeoutMs] is checked only when a
 * chunk arrives, so it is a ceiling for a source that trickles bytes, not a stall detector — a
 * connection that goes quiet produces no chunk and so no check. The real per-read watchdog is the
 * read timeout on the [PackFetcher]'s HTTP client (30 s on the app's shared OkHttpClient); a fetcher
 * injected without one can hang this store indefinitely.
 */
class BergamotModelStore(
    private val rootDir: File,
    private val manifest: BergamotManifest,
    private val fetcher: PackFetcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val stallTimeoutMs: Long = 90_000,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) : OfflineModelStore {

    /** The manifest version every marker and stamp on disk is compared against. */
    private val version: String = manifest.version.toString()

    /**
     * Null until something actually collects [installed]. Seeding this in the constructor would
     * walk every pack directory — and sum the length of every file in each — on whichever thread
     * built the store, which since SettingsViewModel injects it is the main one.
     */
    private val installedFlow = MutableStateFlow<List<InstalledPack>?>(null)

    fun packDir(pair: ModelPair): File = File(rootDir, pair.id)

    /**
     * [File.getUsableSpace] reports 0 for a path that does not exist, and on a first run neither
     * the pack directory nor [rootDir] does — measuring those would refuse every first download on
     * a device with gigabytes free. Walk up to a directory that exists to probe the filesystem
     * without creating anything.
     */
    private fun nearestExisting(dir: File): File =
        generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: dir

    override fun isInstalled(pair: ModelPair): Boolean = isInstalled(packDir(pair))

    override fun installed(): Flow<List<InstalledPack>> = flow {
        if (installedFlow.value == null) {
            val scanned = withContext(ioDispatcher) { scanInstalled() }
            // compareAndSet, not assignment: an ensure() that finished first must not be undone.
            installedFlow.compareAndSet(null, scanned)
        }
        emitAll(installedFlow.filterNotNull())
    }

    override fun ensure(pair: ModelPair): Flow<Float> = flow {
        if (isInstalled(pair)) { emit(1f); return@flow }
        val entry = manifest.find(pair)
            ?: throw OfflinePackDownloadException(pair, 0, IllegalStateException("not in manifest"))
        val needed = (entry.model.size ?: 0L) * 2
        // Checked before the directory is created, so a refused download leaves nothing behind.
        if (usableSpace(nearestExisting(rootDir)) < needed) throw InsufficientStorageException(needed)

        val dir = packDir(pair)
        if (!stampMatches(dir)) dir.deleteRecursively()   // leftovers from another manifest version
        dir.mkdirs()
        File(dir, STAMP).writeText(version)               // first file in, so a later resume can tell

        val files = listOfNotNull(
            entry.model to MODEL,
            entry.vocab to VOCAB,
            entry.targetVocab?.let { it to TARGET_VOCAB },   // en→zh/ja/ko ship split vocabs
            entry.shortlist to SHORTLIST,
        )
        // Progress is byte-weighted on the model; the small files share the remaining 10%.
        val small = files.size - 1
        val weights = listOf(0.9f) + List(small) { 0.1f / small }
        var done = 0f
        // The corrupt-retry restarts at offset 0, which would otherwise drag the bar from ~0.89
        // back to ~0.09; callers are promised a monotonic non-decreasing sequence ending at 1f.
        var maxEmitted = 0f
        for ((i, f) in files.withIndex()) {
            val (mf, name) = f
            var attempt = 0
            while (true) {
                try {
                    downloadOne(pair, dir, mf, name) { frac ->
                        // Capped below 1f: the weights sum to 1f, so the last file's completion would
                        // otherwise emit exactly 1f while the marker is still unwritten, and a caller
                        // waiting for `>= 1f` would stop on a pack that is not installed yet.
                        maxEmitted = maxOf(maxEmitted, done + frac * weights[i]).coerceAtMost(ALMOST_DONE)
                        emit(maxEmitted)
                    }
                    break
                } catch (e: OfflinePackCorruptException) {
                    if (attempt++ >= 1) throw e
                    File(dir, "$name.gz.part").delete()
                }
            }
            done += weights[i]
        }
        File(dir, MARKER).writeText(version)
        // Before the terminal emit, not after: a caller that stops at 1f cancels this producer, and
        // installed() must not stay stale while isInstalled() already reports true.
        installedFlow.value = scanInstalled()
        emit(1f)
    }.flowOn(ioDispatcher)

    private suspend fun downloadOne(
        pair: ModelPair, dir: File, mf: ManifestFile, name: String, progress: suspend (Float) -> Unit,
    ) {
        val part = File(dir, "$name.gz.part")
        val tmp = File(dir, "$name.tmp")
        val final = File(dir, name)
        // Only a file this manifest version put there may be reused; ensure() has already wiped
        // the directory otherwise, so this is the belt to that braces.
        if (final.exists() && stampMatches(dir)) { progress(1f); return }
        val url = "${manifest.baseUrl.trimEnd('/')}/${mf.path}"
        val expectedGz = mf.size  // uncompressed size; used only as a rough progress denominator

        try {
            var offset = part.length()
            var lastByteAt = System.nanoTime()
            val onBytes: suspend (ByteArray, Int) -> Unit = { buf, n ->
                // Ceiling for a trickling source only — see the class KDoc: with no chunk arriving
                // this never runs, so the HTTP client's read timeout is the real watchdog.
                if (System.nanoTime() - lastByteAt > stallTimeoutMs * 1_000_000) throw IOException("stalled")
                lastByteAt = System.nanoTime()
                FileOutputStream(part, /*append=*/true).use { it.write(buf, 0, n) }
                offset += n
                if (expectedGz != null && expectedGz > 0) {
                    progress((offset.toFloat() / expectedGz).coerceIn(0f, 0.99f))
                }
            }
            try {
                fetcher.fetch(url, offset, onBytes)
            } catch (e: RangeNotSupported) {
                part.delete()
                offset = 0
                fetcher.fetch(url, 0, onBytes)
            }
        } catch (e: IOException) {
            throw OfflinePackDownloadException(pair, mf.size ?: 0L, e)
        }

        try {
            GZIPInputStream(part.inputStream().buffered()).use { gin -> tmp.outputStream().use { gin.copyTo(it) } }
        } catch (e: IOException) {
            part.delete(); tmp.delete()
            throw OfflinePackCorruptException(pair)
        }
        if (mf.sha256 != null && sha256(tmp) != mf.sha256) {
            part.delete(); tmp.delete()
            throw OfflinePackCorruptException(pair)
        }
        if (!tmp.renameTo(final)) {
            part.delete(); tmp.delete()
            throw OfflinePackDownloadException(pair, mf.size ?: 0L, IOException("rename failed"))
        }
        part.delete()
        progress(1f)
    }

    override suspend fun delete(pair: ModelPair) {
        packDir(pair).deleteRecursively()
        installedFlow.value = scanInstalled()
    }

    /** A pack is installed only if its marker names the manifest version this store was built on. */
    private fun isInstalled(dir: File): Boolean = holds(File(dir, MARKER))

    /** True when the directory is being built for this manifest version, so its files may be reused. */
    private fun stampMatches(dir: File): Boolean = holds(File(dir, STAMP))

    private fun holds(file: File): Boolean =
        file.exists() && runCatching { file.readText() }.getOrNull() == version

    private fun scanInstalled(): List<InstalledPack> =
        rootDir.listFiles()?.filter { isInstalled(it) }?.mapNotNull { dir ->
            val (s, t) = dir.name.split("-").takeIf { it.size == 2 } ?: return@mapNotNull null
            val src = Language.entries.firstOrNull { it.code == s } ?: return@mapNotNull null
            val tgt = Language.entries.firstOrNull { it.code == t } ?: return@mapNotNull null
            InstalledPack(ModelPair(src, tgt), dir.walk().filter { it.isFile }.sumOf { it.length() })
        }?.sortedBy { it.pair.id } ?: emptyList()

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL = "model.bin"
        const val VOCAB = "vocab.spm"
        const val SHORTLIST = "lex.s2t.bin"
        /** Present only for packs whose manifest entry has `targetVocab` (split src/trg SentencePiece models). */
        const val TARGET_VOCAB = "vocab.trg.spm"
        /** Written last, holding the manifest version the pack was installed under. */
        const val MARKER = "installed.v1"
        /** Written first into a fresh pack directory, so a resume can tell whose leftovers these are. */
        const val STAMP = "manifest.version"
        /** Ceiling for in-flight progress; an exact 1f is emitted only once the pack is installed. */
        private const val ALMOST_DONE = 0.99f
    }
}
