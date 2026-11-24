package com.example.splitreader.data.catalog

import android.net.Uri
import android.os.SystemClock
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import kotlinx.coroutines.CancellationException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * After gutenberg.org fails, the Gutenberg tab uses the fallback for [OPEN_FOR_MS] before asking
 * gutenberg.org again: no hammering a site under maintenance, and the pages of one search come
 * from one backend. [now] is a monotonic clock (injectable for tests).
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
 * - Falls back only when gutenberg.org answers 5xx or cannot be reached (timeout / refused /
 *   unknown host). A 4xx, or a feed that fails to parse, keeps its own error. One try each — no
 *   retry loops.
 * - A failed primary opens the [breaker]: for 10 minutes every search and download goes to the
 *   fallback without asking gutenberg.org. Not when both failed at connection level — that is a
 *   device without network, and it must not hold the tab on the fallback once it is back online.
 * - Books listed by the fallback carry mirror urls and always download from the mirror.
 * - Both failed: the device's network error when both were connection failures; otherwise the
 *   neutral [CatalogUnavailableException].
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
            useFallbackOnly = breaker.isOpen,
            primary = { primary.search(query, page) },
            fallback = { fallback.search(query, page) },
        )

    override suspend fun downloadEpub(book: CatalogBook): Uri =
        withFailover(
            useFallbackOnly = isMirrorUrl(book.epubUrl) || breaker.isOpen,
            primary = { primary.downloadEpub(book) },
            fallback = { fallback.downloadEpub(book) },
        )

    // Both clients share one EpubDownloader path per source + id.
    override fun localUriFor(book: CatalogBook): Uri = primary.localUriFor(book)

    private suspend fun <T> withFailover(
        useFallbackOnly: Boolean,
        primary: suspend () -> T,
        fallback: suspend () -> T,
    ): T {
        if (useFallbackOnly) return fallbackOrFailure(primaryFailure = null, fallback)
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
            throw e
        } catch (e: Exception) {
            val offline = failureKind(e) == Failure.CONNECTION &&
                (primaryFailure == null || failureKind(primaryFailure) == Failure.CONNECTION)
            if (offline) throw primaryFailure ?: e
            if (primaryFailure != null) breaker.open()
            throw e as? CatalogUnavailableException ?: CatalogUnavailableException(source)
        }
    }

    private enum class Failure { UNAVAILABLE, CONNECTION }

    /** Why a call failed, when that is a reason to use the fallback; null otherwise. */
    private fun failureKind(e: Exception): Failure? = when (e) {
        is CatalogUnavailableException -> Failure.UNAVAILABLE // HTTP 5xx (CatalogErrors.kt)
        is SocketTimeoutException, is UnknownHostException, is SocketException -> Failure.CONNECTION
        else -> null
    }
}
