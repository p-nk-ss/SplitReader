package com.example.splitreader.data.catalog

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

/**
 * Project Gutenberg answers 503 + a maintenance page while it is down; the catalog showed the raw
 * "HTTP 503 ". A 5xx from a catalog source, on search or on download, becomes
 * [CatalogUnavailableException] with a readable message. 4xx keep their current messages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogUnavailableTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    /** Answers every request with [code] and an HTML page, without touching the network. */
    private fun answering(code: Int) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 503) "Service Unavailable" else "Error")
            .header("Retry-After", "3600")
            .body("<html><body>Down for maintenance</body></html>".toResponseBody("text/html".toMediaType()))
            .build()
    }.build()

    private fun gutenberg(code: Int): GutenbergCatalogClient {
        val http = answering(code)
        val api = Retrofit.Builder().baseUrl("https://www.gutenberg.org/").client(http).build()
            .create(GutenbergOpdsApi::class.java)
        return GutenbergCatalogClient(api, http, EpubDownloader(context))
    }

    private fun standardEbooks(code: Int) = StandardEbooksCatalogClient(answering(code), EpubDownloader(context))

    private fun book(source: CatalogSource, id: String) = CatalogBook(
        source = source, id = id, title = "T", author = "A", languages = emptyList(), coverUrl = null,
        epubUrl = "https://example.invalid/$id.epub",
    )

    private suspend fun messageOf(block: suspend () -> Unit): Throwable {
        try { block() } catch (e: Throwable) { return e }
        fail("expected a failure"); error("unreachable")
    }

    // Neutral for every source: "Open Library" is a real, separate service (openlibrary.org), and
    // the Project Gutenberg name is kept out of the UI (CatalogSource).
    private val catalogDown = "This catalog is temporarily unavailable. Try again later."
    private val gutenbergDown = catalogDown
    private val standardDown = catalogDown

    @Test fun `a Gutenberg search answered 503 says the catalog is temporarily unavailable`() = runTest {
        val e = messageOf { gutenberg(503).search("", 1) }
        assertEquals(gutenbergDown, e.message)
        assertEquals(CatalogUnavailableException::class, e::class)
    }

    @Test fun `a Gutenberg query answered 500 says the catalog is temporarily unavailable`() = runTest {
        assertEquals(gutenbergDown, messageOf { gutenberg(500).search("dickens", 1) }.message)
    }

    @Test fun `a Gutenberg download answered 503 says the catalog is temporarily unavailable`() = runTest {
        assertEquals(gutenbergDown, messageOf { gutenberg(503).downloadEpub(book(CatalogSource.GUTENBERG, "9999501")) }.message)
    }

    @Test fun `a Standard Ebooks search answered 503 says the catalog is temporarily unavailable`() = runTest {
        assertEquals(standardDown, messageOf { standardEbooks(503).search("", 1) }.message)
    }

    @Test fun `a Standard Ebooks download answered 502 says the catalog is temporarily unavailable`() = runTest {
        assertEquals(standardDown, messageOf { standardEbooks(502).downloadEpub(book(CatalogSource.STANDARD_EBOOKS, "a/b502")) }.message)
    }

    @Test fun `a 404 is not reported as the source being unavailable`() = runTest {
        val search = messageOf { gutenberg(404).search("", 1) }
        assertFalse(search.toString(), search is CatalogUnavailableException)
        val download = messageOf { gutenberg(404).downloadEpub(book(CatalogSource.GUTENBERG, "9999404")) }
        assertEquals("Download failed: HTTP 404", download.message)
    }
}
