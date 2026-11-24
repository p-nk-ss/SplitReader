package com.example.splitreader.data.catalog

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.catalog.gutendex.GutendexApi
import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
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
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

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
                languages = emptyList(), // as gutenberg.org's OPDS rows: no language chip (M-4)
                coverUrl = "https://gutenberg.pglaf.org/cache/epub/98/pg98.cover.medium.jpg",
                epubUrl = "https://gutenberg.pglaf.org/cache/epub/98/pg98-images.epub",
            ),
            book,
        )
        assertTrue("next page exists", page.hasNext)
        assertEquals("Unknown author", page.books.single { it.id == "28198" }.author)
        assertEquals("John Forster", page.books.single { it.id == "25851" }.author)
    }

    // gutenberg.org's OPDS rows (autocat3 BaseSearcher.format_author) are libgutenberg's
    // DublinCore.make_pretty_name per author (split on ", ", reverse, join with spaces, drop
    // "(…)", squeeze spaces) joined by DublinCore.strunk ("A and B", "A, B, and C"). Same here.
    @Test fun `author names read as gutenberg_org's OPDS feed shows them`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-popular.json"))

        val authors = client.search("", 1).books.associate { it.id to it.author }

        assertEquals("Jane Austen", authors["1342"])
        assertEquals("Mary Wollstonecraft Shelley", authors["84"])
        assertEquals("Arthur Conan Doyle", authors["1661"])
        assertEquals("F. Scott Fitzgerald", authors["64317"])
        assertEquals("graf Leo Tolstoy", authors["2600"])
        assertEquals("Alexandre Dumas and Auguste Maquet", authors["1184"])
        assertEquals("Homer", authors["1727"])
    }

    @Test fun `names with a suffix and three authors read as gutenberg_org shows them`() {
        assertEquals("Jr. Martin Luther King", displayName("King, Martin Luther, Jr."))
        assertEquals("Emperor of Rome Marcus Aurelius", displayName("Marcus Aurelius, Emperor of Rome"))
        assertEquals("E. M. Forster", displayName("Forster, E. M. (Edward Morgan)"))
        assertEquals("Jane Austen", authorLine(listOf("Austen, Jane")))
        assertEquals(
            "Mark Twain, Charles Dudley Warner, and Jane Austen",
            authorLine(listOf("Twain, Mark", "Warner, Charles Dudley", "Austen, Jane")),
        )
        assertEquals("Unknown author", authorLine(emptyList()))
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

    // M-6: where a download goes is decided by the breaker, not by which backend listed the book.
    @Test fun `a book listed from Gutendex downloads from gutenberg_org once the breaker is closed`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        hosts[mirrorHost] = fileServer
        val book = client.search("dickens", 1).books.first().copy(id = "9990098", epubUrl = mirrorEpubUrl("9990098"))
        nowMs += 60 * 60_000L // breaker closed again
        hosts[primaryHost] = fileServer
        requests.clear()

        client.downloadEpub(book)

        assertEquals(listOf("https://www.gutenberg.org/ebooks/9990098.epub.images"), requests.map { it.toString() })
    }

    @Test fun `a book listed from Gutendex goes to the mirror when gutenberg_org fails again`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[mirrorHost] = fileServer

        client.downloadEpub(gutenbergBook("9990097").copy(epubUrl = mirrorEpubUrl("9990097")))

        assertEquals(listOf(primaryHost, mirrorHost), requests.map { it.host })
    }

    @Test fun `while the breaker is open a book listed from Gutendex downloads from the mirror`() = runTest {
        openBreaker()
        hosts[mirrorHost] = fileServer

        client.downloadEpub(gutenbergBook("9990096").copy(epubUrl = mirrorEpubUrl("9990096")))

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

    // ── I-1: three kinds of failure (5xx / offline / timeout-reset-stream-TLS) ───────────────

    private suspend fun openBreaker() {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        client.search("dickens", 1)
        assertTrue(breaker.isOpen)
        requests.clear()
    }

    @Test fun `while the breaker is open a Gutendex timeout is the neutral error, not a network error`() = runTest {
        openBreaker()
        hosts[fallbackHost] = failing(SocketTimeoutException("timeout"))

        val e = failureOf { client.search("dracula", 1) }

        assertEquals(CatalogUnavailableException::class, e::class)
        assertTrue("still open", breaker.isOpen)
        assertEquals(emptyList<HttpUrl>(), requestsTo(primaryHost))
    }

    @Test fun `while the breaker is open a mirror download that times out is the neutral error`() = runTest {
        openBreaker()
        hosts[mirrorHost] = failing(SocketTimeoutException("timeout"))

        val e = failureOf { client.downloadEpub(gutenbergBook("9990601")) }

        assertEquals(CatalogUnavailableException::class, e::class)
        assertEquals(emptyList<HttpUrl>(), requestsTo(primaryHost))
    }

    @Test fun `gutenberg_org hanging and Gutendex timing out opens the breaker`() = runTest {
        hosts[primaryHost] = failing(SocketTimeoutException("timeout"))
        hosts[fallbackHost] = failing(SocketTimeoutException("timeout"))

        val e = failureOf { client.search("dickens", 1) }

        assertEquals(CatalogUnavailableException::class, e::class)
        // The next search goes straight to Gutendex: no second 30 s wait on gutenberg.org.
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        assertEquals("A Tale of Two Cities", client.search("dickens", 1).books.first().title)
        assertEquals("gutenberg.org asked once", 1, requestsTo(primaryHost).size)
    }

    @Test fun `gutenberg_org offline and Gutendex timing out is the neutral error`() = runTest {
        hosts[fallbackHost] = failing(SocketTimeoutException("timeout")) // gutenberg.org: unknown host

        val e = failureOf { client.search("dickens", 1) }

        assertEquals(CatalogUnavailableException::class, e::class)
        assertTrue("Gutendex was reached: the device is online", breaker.isOpen)
    }

    @Test fun `timeouts, resets, broken streams and TLS failures on gutenberg_org fall back`() = runTest {
        hosts[fallbackHost] = json(resource("gutendex-search-dickens.json"))
        val failures = listOf<IOException>(
            // OkHttp's Http1ExchangeCodec when the server closes the connection before answering.
            IOException("unexpected end of stream on https://www.gutenberg.org/...", EOFException("\\n not found: limit=0")),
            StreamResetException(ErrorCode.REFUSED_STREAM),
            SSLHandshakeException("Chain validation failed"),
            SocketException("Connection reset"),
            InterruptedIOException("timeout"), // OkHttp's call timeout
            SocketTimeoutException("timeout"),
        )
        for (failure in failures) {
            nowMs += 2 * GutenbergBreaker.OPEN_FOR_MS
            requests.clear()
            hosts[primaryHost] = failing(failure)

            val page = runCatching { client.search("dickens", 1) }

            assertEquals(page.toString(), "A Tale of Two Cities", page.getOrNull()?.books?.first()?.title)
            assertEquals(failure.toString(), listOf(primaryHost, fallbackHost), requests.map { it.host })
            assertTrue(failure.toString(), breaker.isOpen)
        }
    }

    @Test fun `with no network every offline kind keeps the network error`() = runTest {
        for (offline in listOf(UnknownHostException("dns"), ConnectException("refused"), NoRouteToHostException("no route"))) {
            hosts[primaryHost] = failing(offline)
            hosts[fallbackHost] = failing(UnknownHostException("Unable to resolve host \"gutendex.com\""))

            val e = failureOf { client.search("dickens", 1) }

            // The primary's own error (a copy: coroutines' stack-trace recovery re-creates it).
            assertEquals("$offline -> $e", offline.javaClass to offline.message, e.javaClass to e.message)
            assertFalse("$offline", breaker.isOpen)
        }
    }

    // ── I-3: any other failure is not "unavailable" ──────────────────────────────────────────

    @Test fun `while the breaker is open a book missing on the mirror keeps its own error`() = runTest {
        openBreaker()
        hosts[mirrorHost] = status(404)

        val e = failureOf { client.downloadEpub(gutenbergBook("9990404")) }

        assertFalse(e.toString(), e is CatalogUnavailableException)
        assertEquals("Download failed: HTTP 404", e.message)
    }

    @Test fun `a Gutendex answer that is not JSON keeps its own error`() = runTest {
        hosts[primaryHost] = status(503)
        hosts[fallbackHost] = { response(it, 200, "<html>Cloudflare</html>", "text/html") }

        val e = failureOf { client.search("dickens", 1) }

        assertFalse(e.toString(), e is CatalogUnavailableException)
        assertTrue(e.toString(), e is com.google.gson.stream.MalformedJsonException)
        assertTrue("gutenberg.org answered 503: the breaker opens", breaker.isOpen)
    }

    @Test fun `a bug in the fallback keeps its own error`() = runTest {
        openBreaker()
        hosts[mirrorHost] = { throw IllegalStateException("bug") }

        val e = failureOf { client.downloadEpub(gutenbergBook("9990500")) }

        assertEquals(IllegalStateException::class, e::class)
        assertEquals("bug", e.message)
    }

    // ── M-1: cancellation ────────────────────────────────────────────────────────────────────

    // A blocking call (EpubDownloader's execute()) is not interrupted by cancellation: it can fail
    // with a real timeout after the caller is gone. That is the cancellation, not an outage.
    @Test fun `a download cancelled during the gutenberg_org call never reaches the mirror or opens the breaker`() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        hosts[primaryHost] = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw SocketTimeoutException("timeout")
        }
        hosts[mirrorHost] = fileServer

        val job = launch(Dispatchers.IO) { client.downloadEpub(gutenbergBook("9990700")) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(emptyList<HttpUrl>(), requestsTo(mirrorHost))
        assertFalse("a cancellation must not open the breaker", breaker.isOpen)
    }
}
