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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 */
class BergamotModelStore(
    private val rootDir: File,
    private val manifest: BergamotManifest,
    private val fetcher: PackFetcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val stallTimeoutMs: Long = 90_000,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) : OfflineModelStore {

    private val installedFlow = MutableStateFlow(scanInstalled())

    fun packDir(pair: ModelPair): File = File(rootDir, pair.id)

    override fun isInstalled(pair: ModelPair): Boolean = File(packDir(pair), MARKER).exists()

    override fun installed(): Flow<List<InstalledPack>> = installedFlow

    override fun ensure(pair: ModelPair): Flow<Float> = flow {
        if (isInstalled(pair)) { emit(1f); return@flow }
        val entry = manifest.find(pair)
            ?: throw OfflinePackDownloadException(pair, 0, IllegalStateException("not in manifest"))
        val dir = packDir(pair).also { it.mkdirs() }
        val needed = (entry.model.size ?: 0L) * 2
        if (usableSpace(rootDir) < needed) throw InsufficientStorageException(needed)

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
        for ((i, f) in files.withIndex()) {
            val (mf, name) = f
            var attempt = 0
            while (true) {
                try {
                    downloadOne(pair, dir, mf, name) { frac -> emit(done + frac * weights[i]) }
                    break
                } catch (e: OfflinePackCorruptException) {
                    if (attempt++ >= 1) throw e
                    File(dir, "$name.gz.part").delete()
                }
            }
            done += weights[i]
        }
        File(dir, MARKER).writeText("1")
        emit(1f)
        installedFlow.value = scanInstalled()
    }.flowOn(ioDispatcher)

    private suspend fun downloadOne(
        pair: ModelPair, dir: File, mf: ManifestFile, name: String, progress: suspend (Float) -> Unit,
    ) {
        val part = File(dir, "$name.gz.part")
        val tmp = File(dir, "$name.tmp")
        val final = File(dir, name)
        if (final.exists()) { progress(1f); return }
        val url = "${manifest.baseUrl.trimEnd('/')}/${mf.path}"
        val expectedGz = mf.size  // uncompressed size; used only as a rough progress denominator

        try {
            var offset = part.length()
            var lastByteAt = System.nanoTime()
            val onBytes: suspend (ByteArray, Int) -> Unit = { buf, n ->
                // Stall guard: OkHttp's 30 s read timeout is the per-read watchdog; this is the
                // spec's 90 s ceiling for a source that trickles a byte at a time.
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
        if (!tmp.renameTo(final)) throw OfflinePackDownloadException(pair, mf.size ?: 0L, IOException("rename failed"))
        part.delete()
        progress(1f)
    }

    override suspend fun delete(pair: ModelPair) {
        packDir(pair).deleteRecursively()
        installedFlow.value = scanInstalled()
    }

    private fun scanInstalled(): List<InstalledPack> =
        rootDir.listFiles()?.filter { File(it, MARKER).exists() }?.mapNotNull { dir ->
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
        const val MARKER = "installed.v1"
    }
}
