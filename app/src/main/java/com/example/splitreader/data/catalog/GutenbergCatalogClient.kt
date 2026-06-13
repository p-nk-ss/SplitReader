package com.example.splitreader.data.catalog

import android.net.Uri
import com.example.splitreader.di.GutenbergClient
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject
import javax.inject.Singleton

/** Pulls the numeric Gutenberg book id out of an OPDS `<id>`/href, e.g. `…/ebooks/1661.opds` → 1661. */
private val EBOOK_ID_REGEX = Regex("""ebooks/(\d+)""")

/** Project Gutenberg catalog: OPDS search/popular feed parsed with Jsoup, deterministic EPUB/cover URLs. */
@Singleton
class GutenbergCatalogClient @Inject constructor(
    private val api: GutenbergOpdsApi,
    @GutenbergClient private val httpClient: OkHttpClient,
    private val downloader: EpubDownloader,
) : CatalogSourceClient {

    override val source: CatalogSource = CatalogSource.GUTENBERG

    override suspend fun search(query: String, page: Int): CatalogPage =
        withContext(Dispatchers.IO) {
            // No query → show the most-downloaded books so the screen is useful on first open.
            val body = if (query.isBlank()) api.popularBooks() else api.searchBooks(query)
            CatalogPage(books = parseOpds(body.string()), hasNext = false)
        }

    /** Maps OPDS `<entry>` elements to [CatalogBook]; shared by search and the popular feed. */
    private fun parseOpds(xml: String): List<CatalogBook> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("entry").mapNotNull { entry ->
            // The feed leads with "All Books"/"Authors"/"Subjects" navigation entries whose ids
            // have no numeric ebook segment — EBOOK_ID_REGEX naturally filters them out.
            val id = EBOOK_ID_REGEX.find(entry.selectFirst("id")?.text().orEmpty())
                ?.groupValues?.get(1) ?: return@mapNotNull null
            val title = entry.selectFirst("title")?.text()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val author = entry.selectFirst("content")?.text()
                .orEmpty().ifBlank { "Unknown author" }
            CatalogBook(
                source = source,
                id = id,
                title = title,
                author = author,
                languages = emptyList(),
                coverUrl = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg",
                epubUrl = "https://www.gutenberg.org/ebooks/$id.epub.images",
            )
        }
    }

    override suspend fun downloadEpub(book: CatalogBook): Uri =
        downloader.download(book.epubUrl, httpClient, source, book.id)
}
