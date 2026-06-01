package com.example.splitreader.data.catalog

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Project Gutenberg's official OPDS catalog (base URL `https://www.gutenberg.org/`).
 *
 * We hit gutenberg.org directly rather than the Gutendex JSON wrapper, which proved unreliable
 * (chronic timeouts). The search feed returns an Atom/OPDS XML document — parsed with Jsoup in
 * [com.example.splitreader.data.repository.CatalogRepositoryImpl]. The trailing slash on
 * `search.opds/` is required (without it the server issues a slow 301 redirect).
 */
interface GutenbergOpdsApi {

    /** Full-text search over title and author. Returns the raw OPDS XML feed. */
    @GET("ebooks/search.opds/")
    suspend fun searchBooks(@Query("query") query: String): ResponseBody
}
