package com.example.splitreader.data.catalog

import android.net.Uri
import com.example.splitreader.data.catalog.gutendex.GutendexApi
import com.example.splitreader.data.catalog.gutendex.GutendexBook
import com.example.splitreader.di.GutenbergClient
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Project Gutenberg's official mirror (same `cache/epub/<id>/` layout and ids as gutenberg.org). */
internal const val GUTENBERG_MIRROR = "https://gutenberg.pglaf.org"

/**
 * The mirror's copy of the file gutenberg.org serves for `ebooks/<id>.epub.images` (the "older
 * e-readers" EPUB, `pg<id>-images.epub`) — the same file whichever host it comes from, so a book
 * parses into the same chapters from either.
 */
internal fun mirrorEpubUrl(id: String) = "$GUTENBERG_MIRROR/cache/epub/$id/pg$id-images.epub"

internal fun mirrorCoverUrl(id: String) = "$GUTENBERG_MIRROR/cache/epub/$id/pg$id.cover.medium.jpg"

/**
 * The Gutenberg tab's fallback while gutenberg.org is down: metadata from Gutendex, files and
 * covers from the gutenberg.pglaf.org mirror. Same [CatalogSource] and ids as
 * [GutenbergCatalogClient], so a book listed by either is the same book (same local file).
 * Only [FailoverGutenbergClient] calls it.
 */
@Singleton
class GutendexCatalogClient @Inject constructor(
    private val api: GutendexApi,
    @GutenbergClient private val httpClient: OkHttpClient,
    private val downloader: EpubDownloader,
) : CatalogSourceClient {

    override val source: CatalogSource = CatalogSource.GUTENBERG

    override suspend fun search(query: String, page: Int): CatalogPage =
        withContext(Dispatchers.IO) {
            val response = try {
                api.books(search = query.trim().ifBlank { null }, page = page)
            } catch (e: HttpException) {
                throwIfUnavailable(source, e.code())
                throw e
            }
            CatalogPage(
                books = response.results.orEmpty().mapNotNull { it.toCatalogBook() },
                hasNext = response.next != null,
            )
        }

    /** Text, not under copyright, with an EPUB — otherwise null (audio books, restricted titles). */
    private fun GutendexBook.toCatalogBook(): CatalogBook? {
        val id = id?.toString() ?: return null
        if (copyright == true || mediaType != "Text") return null
        if (formats.orEmpty()["application/epub+zip"].isNullOrBlank()) return null
        val title = title?.takeIf { it.isNotBlank() } ?: return null
        val author = authorLine(authors.orEmpty().mapNotNull { it.name })
        return CatalogBook(
            source = source,
            id = id,
            title = title,
            author = author,
            // gutenberg.org's OPDS rows carry no languages, so no language chip: a row looks the
            // same whichever backend listed it.
            languages = emptyList(),
            coverUrl = mirrorCoverUrl(id),
            epubUrl = mirrorEpubUrl(id),
        )
    }

    override suspend fun downloadEpub(book: CatalogBook): Uri =
        downloader.download(mirrorEpubUrl(book.id), httpClient, source, book.id)

    override fun localUriFor(book: CatalogBook): Uri = downloader.localUri(source, book.id)
}

/**
 * Gutendex names are catalogue-style ("Shelley, Mary Wollstonecraft", "Tolstoy, Leo, graf",
 * "Fitzgerald, F. Scott (Francis Scott)"). gutenberg.org's OPDS rows (autocat3
 * `BaseSearcher.format_author`) show each through libgutenberg's `DublinCore.make_pretty_name` and
 * join them with `DublinCore.strunk`; this is the same, so a row reads the same from either
 * backend: "Mary Wollstonecraft Shelley", "graf Leo Tolstoy", "Alexandre Dumas and Auguste Maquet",
 * and, as on gutenberg.org, "Jr. Martin Luther King". No author: "Unknown author", as the primary.
 */
internal fun authorLine(catalogueNames: List<String>): String {
    val names = catalogueNames.map(::displayName).filter(String::isNotBlank)
    return when (names.size) {
        0 -> "Unknown author"
        1, 2 -> names.joinToString(" and ")
        else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
    }
}

/** `make_pretty_name`: reverse the ", "-separated parts, drop "(…)", squeeze spaces. */
internal fun displayName(catalogueName: String): String =
    catalogueName.split(", ").reversed().joinToString(" ")
        .replace(Regex("""\(.*\)"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
