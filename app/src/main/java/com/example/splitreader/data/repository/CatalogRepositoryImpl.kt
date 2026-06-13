package com.example.splitreader.data.repository

import android.net.Uri
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.domain.repository.CatalogRepository
import javax.inject.Inject

/**
 * Thin facade over the per-source [CatalogSourceClient]s (wired via Hilt `@IntoMap`). Routes every
 * call to the client for the requested [CatalogSource]; all networking/parsing lives in the clients.
 */
class CatalogRepositoryImpl @Inject constructor(
    private val clients: Map<CatalogSource, @JvmSuppressWildcards CatalogSourceClient>,
) : CatalogRepository {

    override suspend fun search(query: String, source: CatalogSource, page: Int): CatalogPage =
        client(source).search(query, page)

    override suspend fun downloadEpub(book: CatalogBook): Uri =
        client(book.source).downloadEpub(book)

    private fun client(source: CatalogSource): CatalogSourceClient =
        clients[source] ?: error("No catalog client registered for $source")
}
