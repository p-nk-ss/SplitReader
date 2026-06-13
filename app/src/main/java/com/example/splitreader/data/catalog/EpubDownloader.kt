package com.example.splitreader.data.catalog

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.CatalogSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared atomic EPUB downloader for all catalog sources. Streams into a per-source subfolder of
 * `filesDir/catalog/` so ids from different sources never collide, downloading to a `.part` file
 * first and renaming on success (a failed/partial download never leaves a truncated `*.epub` that
 * the parser would later choke on). Idempotent: returns the existing file if already downloaded.
 */
@Singleton
class EpubDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun download(
        url: String,
        client: OkHttpClient,
        source: CatalogSource,
        fileId: String,
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(File(context.filesDir, "catalog"), source.name.lowercase()).apply { mkdirs() }
        // Ids may contain '/' (Standard Ebooks slugs) — flatten to a safe file name.
        val safeName = fileId.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val target = File(dir, "$safeName.epub")
        // Reuse only a genuine zip; a stale HTML/partial file (e.g. an interstitial saved as .epub)
        // is re-downloaded instead of being handed to the parser again.
        if (target.exists() && target.length() > 0 && looksLikeZip(target)) {
            return@withContext Uri.fromFile(target)
        }

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val tmp = File(dir, "$safeName.epub.part")
            body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
        Uri.fromFile(target)
    }

    /** True if [file] starts with the ZIP local-file-header magic "PK" — i.e. looks like an EPUB. */
    private fun looksLikeZip(file: File): Boolean =
        runCatching {
            file.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }
        }.getOrDefault(false)
}
