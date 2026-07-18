package com.example.splitreader.domain.catalog

import android.net.Uri
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage
import com.example.splitreader.domain.model.CatalogSource

/**
 * Discovery + download for a single catalog [source]. Implementations own the source's networking
 * and parsing; the catalog repository implementation routes to them by source. Mirrors the
 * [com.example.splitreader.domain.translator.TranslationProviderApi] multibinding.
 */
interface CatalogSourceClient {
    val source: CatalogSource

    /** Searches the source. Blank [query] yields a default listing (popular / newest). [page] is 1-based. */
    suspend fun search(query: String, page: Int): CatalogPage

    /**
     * Downloads [book]'s EPUB into app-private storage and returns a `file://` [Uri] ready for the
     * parser. Idempotent: returns the existing file if already downloaded.
     */
    suspend fun downloadEpub(book: CatalogBook): Uri
}
