package com.example.splitreader.data.catalog

import android.net.Uri
import com.example.splitreader.di.StandardEbooksClient
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val SE_HOST = "https://standardebooks.org"

/**
 * Standard Ebooks catalog. The official OPDS feed is gated behind Patrons Circle membership, so we
 * scrape the fully public ebook listing HTML (`/ebooks?query=…`) with Jsoup — the same approach used
 * for Gutenberg's OPDS. Each `<li typeof="schema:Book">` yields an `/ebooks/{author}/{title}` slug;
 * the EPUB and cover URLs are derived deterministically from that slug (no per-book page fetch).
 */
@Singleton
class StandardEbooksCatalogClient @Inject constructor(
    @StandardEbooksClient private val httpClient: OkHttpClient,
    private val downloader: EpubDownloader,
) : CatalogSourceClient {

    override val source: CatalogSource = CatalogSource.STANDARD_EBOOKS

    override suspend fun search(query: String, page: Int): CatalogPage =
        withContext(Dispatchers.IO) {
            val urlBuilder = "$SE_HOST/ebooks".toHttpUrl().newBuilder()
            if (query.isNotBlank()) urlBuilder.addQueryParameter("query", query)
            val request = Request.Builder().url(urlBuilder.build()).build()
            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Search failed: HTTP ${response.code}")
                response.body?.string() ?: throw IOException("Empty response body")
            }
            CatalogPage(books = parseListing(html), hasNext = false)
        }

    private fun parseListing(html: String): List<CatalogBook> {
        val doc = Jsoup.parse(html, SE_HOST)
        return doc.select("li[typeof=schema:Book]").mapNotNull { li ->
            // The <li about="/ebooks/{author}/{title}"> attribute is the stable slug id. Fall back to
            // the title link's href if absent. (The thumbnail link's text is empty — don't use it.)
            val slug = li.attr("about").ifBlank { li.selectFirst("p a[property=schema:url]")?.attr("href").orEmpty() }
                .removePrefix(SE_HOST).removePrefix("/ebooks/").trim('/')
            if (slug.isBlank() || !slug.contains('/')) return@mapNotNull null
            // First schema:name in the entry is the title; the author carries class="author".
            val title = li.selectFirst("[property=schema:name]")?.text()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val author = li.selectFirst("p.author [property=schema:name]")?.text()
                ?.takeIf { it.isNotBlank() } ?: "Unknown author"
            // Cover comes straight from the listing thumbnail; EPUB filename joins the slug with '_'.
            val coverUrl = li.selectFirst("img[property=schema:image]")?.absUrl("src")?.takeIf { it.isNotBlank() }
            val fileName = slug.replace('/', '_')
            CatalogBook(
                source = source,
                id = slug,
                title = title,
                author = author,
                languages = listOf("en"),
                coverUrl = coverUrl ?: "$SE_HOST/ebooks/$slug/downloads/cover.jpg",
                // Without ?source=download the URL serves a "Your Download Has Started!" HTML
                // interstitial; the query param makes the server return the real epub binary.
                epubUrl = "$SE_HOST/ebooks/$slug/downloads/$fileName.epub?source=download",
            )
        }
    }

    override suspend fun downloadEpub(book: CatalogBook): Uri =
        downloader.download(book.epubUrl, httpClient, source, book.id)
}
