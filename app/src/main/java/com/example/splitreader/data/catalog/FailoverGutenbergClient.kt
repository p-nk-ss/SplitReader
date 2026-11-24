package com.example.splitreader.data.catalog

import android.net.Uri
import android.os.SystemClock
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.CancellationException
import okhttp3.internal.http2.StreamResetException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * After gutenberg.org fails, the Gutenberg tab uses the fallback for [OPEN_FOR_MS] before asking
 * gutenberg.org again: no hammering a site under maintenance. It is a time window, not a pin per
 * search: each call is routed by the state at that moment (the catalog screen only ever asks for
 * page 1, so a search's pages cannot be split across backends today). [now] is a monotonic clock
 * (injectable for tests).
 */
@Singleton
class GutenbergBreaker(private val now: () -> Long) {

    @Inject constructor() : this({ SystemClock.elapsedRealtime() })

    @Volatile private var openUntil: Long = Long.MIN_VALUE

    val isOpen: Boolean get() = now() < openUntil

    fun open() {
        openUntil = now() + OPEN_FOR_MS
    }

    companion object {
        const val OPEN_FOR_MS = 10 * 60_000L
    }
}

/**
 * The Gutenberg tab's [CatalogSourceClient] (bound in `di/CatalogModule`): gutenberg.org
 * ([primary], OPDS) first; Gutendex + the gutenberg.pglaf.org mirror ([fallback]) when it is down.
 *
 * Failures come in three kinds ([Failure]); anything else (a 4xx, a mirror 404, a feed or JSON that
 * fails to parse, a bug) is not an outage and is rethrown unchanged, from either backend, so it
 * reaches the screen and crash reporting as itself. One try each, no retry loops.
 * - The primary failed with any of the three kinds: the fallback is tried once. The breaker opens
 *   (for 10 minutes every search and download goes to the fallback without asking gutenberg.org)
 *   unless both failed [Failure.OFFLINE]: that is a device without network, and it must not hold
 *   the tab on the fallback once it is back online.
 * - The fallback failed with one of the three kinds: the device's network error when it failed
 *   [Failure.OFFLINE] and the primary did too (or was not asked); otherwise the neutral
 *   [CatalogUnavailableException].
 * - Downloads are routed by the breaker, not by which backend listed the book: a book listed by
 *   the fallback (mirror urls) is fetched from gutenberg.org, by id, while the breaker is closed.
 */
@Singleton
class FailoverGutenbergClient @Inject constructor(
    private val primary: GutenbergCatalogClient,
    private val fallback: GutendexCatalogClient,
    private val breaker: GutenbergBreaker,
) : CatalogSourceClient {

    override val source: CatalogSource = CatalogSource.GUTENBERG

    override suspend fun search(query: String, page: Int): CatalogPage =
        withFailover(
            primary = { primary.search(query, page) },
            fallback = { fallback.search(query, page) },
        )

    override suspend fun downloadEpub(book: CatalogBook): Uri =
        withFailover(
            primary = { primary.downloadEpub(book.copy(epubUrl = gutenbergEpubUrl(book.id))) },
            fallback = { fallback.downloadEpub(book) },
        )

    // Both clients share one EpubDownloader path per source + id.
    override fun localUriFor(book: CatalogBook): Uri = primary.localUriFor(book)

    private suspend fun <T> withFailover(primary: suspend () -> T, fallback: suspend () -> T): T {
        if (breaker.isOpen) return fallbackOrFailure(primaryFailure = null, fallback)
        val primaryFailure = try {
            return primary()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (failureKind(e) == null) throw e
            e
        }
        return fallbackOrFailure(primaryFailure, fallback)
    }

    /** Runs [fallback] once; on failure maps it per the class rules ([primaryFailure] null = breaker open). */
    private suspend fun <T> fallbackOrFailure(primaryFailure: Exception?, fallback: suspend () -> T): T {
        try {
            val result = fallback()
            if (primaryFailure != null) breaker.open()
            return result
        } catch (e: CancellationException) {
            // Load-bearing: a caller cancelled while the primary's blocking call was failing lands
            // here (the fallback's withContext throws on entry). Not an outage: no breaker.
            throw e
        } catch (e: Exception) {
            val kind = failureKind(e)
            val offline = kind == Failure.OFFLINE &&
                (primaryFailure == null || failureKind(primaryFailure) == Failure.OFFLINE)
            if (offline) throw primaryFailure ?: e
            if (primaryFailure != null) breaker.open()
            if (kind == null) throw e
            throw e as? CatalogUnavailableException ?: CatalogUnavailableException(source)
        }
    }

    private enum class Failure {
        /** HTTP 5xx ([CatalogUnavailableException], CatalogErrors.kt). */
        UNAVAILABLE,

        /** Nothing could be reached: no DNS, connection refused, no route. */
        OFFLINE,

        /** Reached, then stalled or dropped: timeouts, resets, a cut stream, an HTTP/2 reset, TLS. */
        UNREACHABLE,
    }

    /** Why a call failed, when that is a reason to use the fallback; null otherwise. */
    private fun failureKind(e: Exception): Failure? = when {
        e is CatalogUnavailableException -> Failure.UNAVAILABLE
        // ConnectException and NoRouteToHostException are SocketExceptions: before UNREACHABLE.
        e is UnknownHostException || e is ConnectException || e is NoRouteToHostException -> Failure.OFFLINE
        e is InterruptedIOException || e is SocketException || e is StreamResetException ||
            e is SSLException || e is EOFException -> Failure.UNREACHABLE
        // OkHttp's "unexpected end of stream on …" (an IOException caused by an EOFException): the
        // server closed the connection unanswered. The chain, not just the cause: coroutines'
        // stack-trace recovery may wrap the original in a copy.
        e is IOException && generateSequence(e.cause) { it.cause }.take(4).any { it is EOFException } ->
            Failure.UNREACHABLE
        else -> null
    }
}
