package com.example.splitreader.domain.repository

import android.net.Uri
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogPage

/** Discovery + download of free public-domain books for the in-app catalog. */
interface CatalogRepository {

    /**
     * Searches the catalog. [languages] is a list of ISO codes to restrict results
     * (empty = no language filter). [page] is 1-based.
     */
    suspend fun search(query: String, languages: List<String>, page: Int): CatalogPage

    /**
     * Downloads [book]'s EPUB into app-private storage and returns a `file://` [Uri] ready to
     * hand to the parser. Idempotent: returns the existing file if already downloaded.
     */
    suspend fun downloadEpub(book: CatalogBook): Uri
}
