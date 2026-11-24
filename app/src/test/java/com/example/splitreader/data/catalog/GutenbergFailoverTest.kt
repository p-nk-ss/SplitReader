package com.example.splitreader.data.catalog

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.catalog.gutendex.GutendexApi
import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * gutenberg.org was down for maintenance (503 + Retry-After) for over a week. The Gutenberg tab
 * ("Open Library") then falls back to Gutendex (metadata) + the gutenberg.pglaf.org mirror (files).
 *
 * No network: one OkHttp interceptor answers per host ([hosts]) and records every request. A host
 * with no route fails at connection level (UnknownHostException), like a device without network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GutenbergFailoverTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private val primaryHost = "www.gutenberg.org"
    private val fallbackHost = "gutendex.com"
    private val mirrorHost = "gutenberg.pglaf.org"

    /** host → answer. Absent = connection failure. */
    private val hosts = mutableMapOf<String, (Request) -> Response>()
    private val requests = mutableListOf<HttpUrl>()
    private var nowMs = 1_000_000L

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request.url
        val answer = hosts[request.url.host] ?: throw UnknownHostException("Unable to resolve host \"${request.url.host}\"")
        answer(request)
    }.build()

    private val downloader = EpubDownloader(context)
    private val breaker = GutenbergBreaker { nowMs }

    private val client: FailoverGutenbergClient by lazy {
        val opds = Retrofit.Builder().baseUrl("https://$primaryHost/").client(http).build()
            .create(GutenbergOpdsApi::class.java)
        val gutendex = Retrofit.Builder().baseUrl("https://$fallbackHost/").client(http)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(GutendexApi::class.java)
        FailoverGutenbergClient(
            primary = GutenbergCatalogClient(opds, http, downloader),
            fallback = GutendexCatalogClient(gutendex, http, downloader),
            breaker = breaker,
        )
    }

    // ── answers ──────────────────────────────────────────────────────────────────────────────

    private fun response(request: Request, code: Int, body: String, type: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("M")
        .body(body.toResponseBody(type.toMediaType())).build()

    private fun status(code: Int): (Request) -> Response = { response(it, code, "<html>Down for maintenance</html>", "text/html") }
    private fun failing(e: IOException): (Request) -> Response = { throw e }
    private fun json(body: String): (Request) -> Response = { response(it, 200, body, "application/json") }
    private fun resource(name: String): String =
        javaClass.classLoader!!.getResource("catalog/$name")!!.readText()

    private val opdsFeed = """<?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry><id>https://www.gutenberg.org/ebooks/1342.opds</id><title>Pride and Prejudice</title>
            <content type="text">Jane Austen</content></entry>
        </feed>"""
    private val primaryUp: (Request) -> Response = { response(it, 200, opdsFeed, "application/atom+xml") }
    private val epubBytes = "PK\u0003\u0004 fake epub"
    private val fileServer: (Request) -> Response = { response(it, 200, epubBytes, "application/epub+zip") }

    private fun requestsTo(host: String) = requests.filter { it.host == host }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try { block() } catch (e: Throwable) { return e }
        fail("expected a failure"); error("unreachable")
    }

    private fun gutenbergBook(id: String) = CatalogBook(
        source = CatalogSource.GUTENBERG, id = id, title = "T", author = "A", languages = emptyList(),
        coverUrl = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg",
        epubUrl = "https://www.gutenberg.org/ebooks/$id.epub.images",
    )

    private fun titles(page: CatalogPage) = page.books.map { it.title }

    // ── search fallback ──────────────────────────────────────────────────────────────────────

    @Test fun `a search answered 503 by gutenberg_org is answered from Gutendex`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        val page = client.search("dickens", 1)

        assertEquals("A Tale of Two Cities", page.books.first().title)
        val asked = requestsTo(fallbackHost).single()
        assertEquals("/books/", asked.encodedPath)
        assertEquals("dickens", asked.queryParameter("search"))
        assertEquals("1", asked.queryParameter("page"))
    }

    @Test fun `the popular list falls back to Gutendex without a search parameter`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-popular.json"))

        val page = client.search("", 1)

        assertEquals("Pride and Prejudice", page.books.first().title)
        val asked = requestsTo(fallbackHost).single()
        assertEquals(null, asked.queryParameter("search"))
        assertEquals("1", asked.queryParameter("page"))
    }

    @Test fun `a connection failure to gutenberg_org falls back to Gutendex`() = runTest {
        hosts[primaryHost] = failing(SocketTimeoutException("timeout"))
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        assertEquals("A Tale of Two Cities", client.search("dickens", 1).books.first().title)
    }

    @Test fun `a refused connection to gutenberg_org falls back to Gutendex`() = runTest {
        hosts[primaryHost] = failing(ConnectException("Failed to connect"))
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        assertEquals("A Tale of Two Cities", client.search("dickens", 1).books.first().title)
    }

    @Test fun `a 404 from gutenberg_org is not a reason to fall back`() = runTest {
        hosts[primaryHost] = status(404)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        val e = failureOf { client.search("dickens", 1) }

        assertFalse(e.toString(), e is CatalogUnavailableException)
        assertTrue(e.toString(), e is retrofit2.HttpException)
        assertEquals(emptyList<HttpUrl>(), requestsTo(fallbackHost))
    }

    // ── circuit breaker ──────────────────────────────────────────────────────────────────────

    @Test fun `after a primary failure every search uses Gutendex for 10 minutes, then gutenberg_org again`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        client.search("dickens", 1)
        assertEquals(1, requestsTo(primaryHost).size)

        // gutenberg.org is back, but the breaker keeps one backend for 10 minutes.
        hosts[primaryHost] = primaryUp
        nowMs += 10 * 60_000L - 1
        assertEquals("A Tale of Two Cities", client.search("dickens", 2).books.first().title)
        assertEquals("not asked again inside 10 min", 1, requestsTo(primaryHost).size)

        nowMs += 1
        assertEquals(listOf("Pride and Prejudice"), titles(client.search("austen", 1)))
        assertEquals(2, requestsTo(primaryHost).size)
        assertEquals(2, requestsTo(fallbackHost).size)
    }

    // ── both down ────────────────────────────────────────────────────────────────────────────

    @Test fun `gutenberg_org 503 and Gutendex 503 is the neutral catalog-unavailable error`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = status(503)

        val e = failureOf { client.search("dickens", 1) }

        assertEquals(CatalogUnavailableException::class, e::class)
        assertEquals("This catalog is temporarily unavailable. Try again later.", e.message)
        assertEquals("one try each, no retries", listOf(1, 1), listOf(requestsTo(primaryHost).size, requestsTo(fallbackHost).size))
    }

    @Test fun `gutenberg_org 503 and Gutendex unreachable is the neutral catalog-unavailable error`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = failing(SocketTimeoutException("timeout"))

        assertEquals(CatalogUnavailableException::class, failureOf { client.search("dickens", 1) }::class)

        // gutenberg.org did answer (503): the device is online and the breaker is open.
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        client.search("dickens", 1)
        assertEquals("gutenberg.org not asked again", 1, requestsTo(primaryHost).size)
    }

    @Test fun `with no network at all the search keeps its network error`() = runTest {
        // Neither host answers: the device is offline.
        val e = failureOf { client.search("dickens", 1) }

        assertEquals(UnknownHostException::class, e::class)
        assertEquals("Unable to resolve host \"$primaryHost\"", e.message)
    }

    @Test fun `being offline does not hold the tab on Gutendex once the network is back`() = runTest {
        failureOf { client.search("dickens", 1) }
        hosts[primaryHost] = primaryUp
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        assertEquals(listOf("Pride and Prejudice"), titles(client.search("austen", 1)))
    }

    @Test fun `while the breaker is open an offline search keeps its network error`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        client.search("dickens", 1)
        hosts.clear() // aeroplane mode

        val e = failureOf { client.search("dracula", 1) }

        assertEquals(UnknownHostException::class, e::class)
    }

    @Test fun `while the breaker is open a Gutendex 503 is the neutral catalog-unavailable error`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        client.search("dickens", 1)
        hosts[fallbackHost] = status(503)

        assertEquals(CatalogUnavailableException::class, failureOf { client.search("dracula", 1) }::class)
    }

    // ── Gutendex mapping (real responses) ────────────────────────────────────────────────────

    @Test fun `Gutendex results skip audio books, copyrighted books and books without an EPUB`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        val ids = client.search("dickens", 1).books.map { it.id }

        // 32 results, 11 of them LibriVox audio (media_type "Sound", copyright true, no EPUB).
        assertEquals(21, ids.size)
        assertFalse("9695 is an audio book", "9695" in ids)
        assertEquals(listOf("98", "1400", "46"), ids.take(3))
    }

    @Test fun `a copyrighted text and a text without an EPUB are skipped`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-popular.json"))

        val ids = client.search("", 1).books.map { it.id }

        assertFalse("5200 Metamorphosis is marked copyright", "5200" in ids)
        assertEquals(31, ids.size)

        hosts[fallbackHost] = json(
            """{"count":2,"next":null,"previous":null,"results":[
              {"id":1,"title":"No epub","authors":[],"languages":["en"],"copyright":false,"media_type":"Text",
               "formats":{"text/html":"https://www.gutenberg.org/ebooks/1.html.images"}},
              {"id":2,"title":"Has epub","authors":[],"languages":["en"],"copyright":false,"media_type":"Text",
               "formats":{"application/epub+zip":"https://www.gutenberg.org/ebooks/2.epub3.images"}}]}""",
        )
        assertEquals(listOf("2"), client.search("x", 1).books.map { it.id })
    }

    @Test fun `Gutendex books are mapped like the OPDS feed, with mirror cover and EPUB urls`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))

        val page = client.search("dickens", 1)
        val book = page.books.first()

        assertEquals(
            CatalogBook(
                source = CatalogSource.GUTENBERG,
                id = "98",
                title = "A Tale of Two Cities",
                author = "Charles Dickens",
                languages = listOf("en"),
                coverUrl = "https://gutenberg.pglaf.org/cache/epub/98/pg98.cover.medium.jpg",
                epubUrl = "https://gutenberg.pglaf.org/cache/epub/98/pg98-images.epub",
            ),
            book,
        )
        assertTrue("next page exists", page.hasNext)
        assertEquals("Unknown author", page.books.single { it.id == "28198" }.author)
        assertEquals("John Forster", page.books.single { it.id == "25851" }.author)
    }

    @Test fun `author names read first name first, as the OPDS feed shows them`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-popular.json"))

        val authors = client.search("", 1).books.associate { it.id to it.author }

        assertEquals("Jane Austen", authors["1342"])
        assertEquals("Mary Wollstonecraft Shelley", authors["84"])
        assertEquals("Arthur Conan Doyle", authors["1661"])
        assertEquals("F. Scott Fitzgerald", authors["64317"])
        assertEquals("graf Leo Tolstoy", authors["2600"])
        assertEquals("Alexandre Dumas, Auguste Maquet", authors["1184"])
        assertEquals("Homer", authors["1727"])
    }

    // ── downloads ────────────────────────────────────────────────────────────────────────────

    @Test fun `a download answered 503 by gutenberg_org is fetched once from the mirror`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[mirrorHost] = fileServer
        val book = gutenbergBook("9990001")

        val uri = client.downloadEpub(book)

        assertEquals(client.localUriFor(book), uri)
        assertEquals(epubBytes, File(uri.path!!).readText())
        assertEquals(
            listOf("https://gutenberg.pglaf.org/cache/epub/9990001/pg9990001-images.epub"),
            requestsTo(mirrorHost).map { it.toString() },
        )
    }

    @Test fun `a download that cannot reach gutenberg_org is fetched from the mirror`() = runTest {
        hosts[primaryHost] = failing(ConnectException("Failed to connect"))
        hosts[mirrorHost] = fileServer

        val uri = client.downloadEpub(gutenbergBook("9990002"))

        assertEquals(epubBytes, File(uri.path!!).readText())
    }

    @Test fun `a book listed from Gutendex downloads from the mirror only`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        hosts[mirrorHost] = fileServer
        val book = client.search("dickens", 1).books.first().copy(id = "9990098", epubUrl = "https://gutenberg.pglaf.org/cache/epub/9990098/pg9990098-images.epub")
        nowMs += 60 * 60_000L // breaker closed again: the listed book still goes to the mirror
        hosts[primaryHost] = primaryUp
        requests.clear()

        client.downloadEpub(book)

        assertEquals(listOf(mirrorHost), requests.map { it.host })
    }

    @Test fun `while the breaker is open a download goes straight to the mirror`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        hosts[mirrorHost] = fileServer
        client.search("dickens", 1)
        requests.clear()

        client.downloadEpub(gutenbergBook("9990003"))

        assertEquals(listOf(mirrorHost), requests.map { it.host })
    }

    @Test fun `a download answered 404 by gutenberg_org is not retried on the mirror`() = runTest {
        hosts[primaryHost] = status(404)
        hosts[mirrorHost] = fileServer

        val e = failureOf { client.downloadEpub(gutenbergBook("9990404")) }

        assertEquals("Download failed: HTTP 404", e.message)
        assertEquals(emptyList<HttpUrl>(), requestsTo(mirrorHost))
    }

    @Test fun `a download with gutenberg_org and the mirror both 503 is the neutral error`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[mirrorHost] = status(503)

        val e = failureOf { client.downloadEpub(gutenbergBook("9990503")) }

        assertEquals(CatalogUnavailableException::class, e::class)
        assertEquals(1, requestsTo(mirrorHost).size)
    }

    @Test fun `an offline download keeps its network error`() = runTest {
        val e = failureOf { client.downloadEpub(gutenbergBook("9990000")) }

        assertEquals(UnknownHostException::class, e::class)
        assertEquals("Unable to resolve host \"$primaryHost\"", e.message)
    }
}
