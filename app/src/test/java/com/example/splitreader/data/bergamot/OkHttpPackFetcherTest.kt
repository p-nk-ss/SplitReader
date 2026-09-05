package com.example.splitreader.data.bergamot

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The fetcher is the store's only door to the network, and resume is the whole reason it exists:
 * a pack is tens of megabytes over a phone connection, so a cut transfer must continue rather than
 * start again. Driven against a real loopback server so the assertions are on the bytes actually
 * sent and received, including the `Range` header the store depends on.
 */
class OkHttpPackFetcherTest {
    private lateinit var server: MockWebServer
    private lateinit var fetcher: OkHttpPackFetcher

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        fetcher = OkHttpPackFetcher(OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    private fun url() = server.url("/models/model.gz").toString()

    /** Collects every chunk, so a test can assert on the whole body rather than on call counts. */
    private class Sink {
        private val out = ByteArrayOutputStream()
        val received: ByteArray get() = out.toByteArray()
        suspend fun onChunk(buf: ByteArray, n: Int) = out.write(buf, 0, n)
    }

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    private val payload = ByteArray(200_000) { (it % 251).toByte() }   // > one 64 KB read buffer

    @Test
    fun `an unranged fetch sends no Range header and delivers every byte`() = runTest {
        server.enqueue(MockResponse().setBody(body(payload)))
        val sink = Sink()

        val total = fetcher.fetch(url(), offset = 0, onChunk = sink::onChunk)

        assertNull("offset 0 must not ask for a range", server.takeRequest().getHeader("Range"))
        assertEquals(payload.size.toLong(), total)
        assertArrayEqualsBytes(payload, sink.received)
    }

    @Test
    fun `a resumed fetch asks for the remainder and delivers what comes back`() = runTest {
        val offset = 64_000L
        val remainder = payload.copyOfRange(offset.toInt(), payload.size)
        server.enqueue(MockResponse().setResponseCode(206).setBody(body(remainder)))
        val sink = Sink()

        val total = fetcher.fetch(url(), offset, sink::onChunk)

        assertEquals("bytes=$offset-", server.takeRequest().getHeader("Range"))
        assertEquals(remainder.size.toLong(), total)
        assertArrayEqualsBytes(remainder, sink.received)
    }

    /**
     * A server that ignores Range answers 200 with the *whole* file. Appending that to a part file
     * would splice the first N bytes in twice and produce a corrupt pack, so it must be refused
     * loudly enough for the store to restart from zero.
     */
    @Test
    fun `a server that ignores Range is refused rather than appended`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(payload)))
        val sink = Sink()

        val err = runCatching { fetcher.fetch(url(), offset = 1000, onChunk = sink::onChunk) }.exceptionOrNull()

        assertTrue("$err", err is RangeNotSupported)
        assertEquals("nothing may be handed to the caller", 0, sink.received.size)
    }

    @Test
    fun `a non-2xx response is an IOException naming the status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val err = runCatching { fetcher.fetch(url(), 0) { _, _ -> } }.exceptionOrNull()

        assertTrue("$err", err is IOException)
        assertTrue("$err", err!!.message!!.contains("404"))
    }

    @Test
    fun `an empty 200 body delivers nothing and reports zero bytes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sink = Sink()

        val total = fetcher.fetch(url(), 0, sink::onChunk)

        assertEquals(0L, total)
        assertEquals(0, sink.received.size)
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals("length", expected.size, actual.size)
        assertTrue("bytes differ", expected.contentEquals(actual))
    }
}
