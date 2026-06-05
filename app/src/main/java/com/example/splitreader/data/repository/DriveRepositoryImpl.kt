package com.example.splitreader.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.splitreader.di.DriveClient
import com.example.splitreader.domain.model.DrivePickedFile
import com.example.splitreader.domain.repository.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Drive REST v3. drive.file grants access to picked files; download needs `alt=media`. */
private const val DRIVE_FILES_BASE = "https://www.googleapis.com/drive/v3/files"

/** File extensions our parsers recognise; used to keep the local file name routable. */
private val BOOK_EXTENSIONS = setOf("epub", "fb2", "mobi", "prc", "azw", "azw3")

/** Drive mime → extension, for files whose name carries no recognisable book extension. */
private val MIME_TO_EXTENSION = mapOf(
    "application/epub+zip" to "epub",
    "application/x-mobipocket-ebook" to "mobi",
    "application/x-fictionbook+xml" to "fb2",
)

class DriveRepositoryImpl @Inject constructor(
    @DriveClient private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) : DriveRepository {

    override suspend fun downloadFile(file: DrivePickedFile, accessToken: String): Uri =
        withContext(Dispatchers.IO) {
            // The picker hands back only an id, so resolve the real name/mime — used only to pick a
            // known extension when possible. We never force a wrong one: an unknown extension is
            // left off so ParseBookUseCase routes by header bytes (Fb2/Epub/Mobi all self-detect).
            val (name, mimeType) = file.name.takeIf { it.isNotBlank() }
                ?.let { it to file.mimeType }
                ?: fetchMetadata(file.fileId, accessToken)
            val ext = extensionFor(name, mimeType)
            Log.d("DRIVE", "download id=${file.fileId} name=$name mime=$mimeType -> ext=$ext")

            val dir = File(context.filesDir, "drive").apply { mkdirs() }
            val target = File(dir, if (ext != null) "${file.fileId}.$ext" else file.fileId)
            if (target.exists() && target.length() > 0) return@withContext Uri.fromFile(target)

            val request = Request.Builder()
                .url("$DRIVE_FILES_BASE/${file.fileId}?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Drive download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                // A JSON body on a 200 means Drive returned an error payload (abuse/scan/etc.) rather
                // than the file — surface it instead of saving a blob the parser can't read.
                if (response.header("Content-Type")?.startsWith("application/json") == true) {
                    throw IOException("Drive returned an error: ${body.string().take(300)}")
                }
                // Stream to a temp file first so a failed/partial download never leaves a truncated
                // file that the parser would later choke on (same guard as the catalog download).
                val tmp = File(dir, "${file.fileId}.part")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
            Log.d("DRIVE", "saved ${target.name} (${target.length()} bytes)")
            Uri.fromFile(target)
        }

    private fun fetchMetadata(fileId: String, accessToken: String): Pair<String, String?> {
        val request = Request.Builder()
            .url("$DRIVE_FILES_BASE/$fileId?fields=name,mimeType")
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive metadata failed: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val name = json.optString("name").ifBlank { fileId }
            val mime = json.optString("mimeType").takeIf { it.isNotBlank() }
            return name to mime
        }
    }

    /**
     * Prefer the real file extension, then the mime mapping. Returns null when neither is a known
     * book type — the caller then saves with no extension and lets the parser detect by content,
     * rather than forcing a wrong extension (e.g. `.epub`) that mis-routes the file.
     */
    private fun extensionFor(name: String, mimeType: String?): String? {
        name.substringAfterLast('.', "").lowercase().let { ext ->
            if (ext in BOOK_EXTENSIONS) return ext
        }
        return MIME_TO_EXTENSION[mimeType]
    }
}
