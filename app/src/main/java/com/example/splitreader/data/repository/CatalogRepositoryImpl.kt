package com.example.splitreader.data.repository

import android.content.Context
import android.net.Uri
import com.example.splitreader.data.catalog.GutenbergOpdsApi
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.repository.CatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Pulls the numeric Gutenberg book id out of an OPDS `<id>`/href, e.g. `…/ebooks/1661.opds` → 1661. */
private val EBOOK_ID_REGEX = Regex("""ebooks/(\d+)""")

class CatalogRepositoryImpl @Inject constructor(
    private val api: GutenbergOpdsApi,
    private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) : CatalogRepository {

    override suspend fun search(query: String, languages: List<String>, page: Int): CatalogPage =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext CatalogPage(emptyList(), hasNext = false)

            val xml = api.searchBooks(query).string()
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            val books = doc.select("entry").mapNotNull { entry ->
                // The feed leads with "Authors"/"Subjects" navigation entries whose ids have no
                // numeric ebook segment — EBOOK_ID_REGEX naturally filters them out.
                val id = EBOOK_ID_REGEX.find(entry.selectFirst("id")?.text().orEmpty())
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                val title = entry.selectFirst("title")?.text()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val author = entry.selectFirst("content")?.text()
                    .orEmpty().ifBlank { "Unknown author" }
                CatalogBook(
                    id = id,
                    title = title,
                    author = author,
                    languages = emptyList(),
                    coverUrl = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg",
                    epubUrl = "https://www.gutenberg.org/ebooks/$id.epub.images",
                )
            }
            CatalogPage(books = books, hasNext = false)
        }

    override suspend fun downloadEpub(book: CatalogBook): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "catalog").apply { mkdirs() }
        val target = File(dir, "${book.id}.epub")
        if (target.exists() && target.length() > 0) return@withContext Uri.fromFile(target)

        val request = Request.Builder().url(book.epubUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            // Stream to a temp file first so a failed/partial download never leaves a
            // truncated *.epub that the parser would later choke on.
            val tmp = File(dir, "${book.id}.epub.part")
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
}
