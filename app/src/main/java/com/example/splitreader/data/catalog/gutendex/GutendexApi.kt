package com.example.splitreader.data.catalog.gutendex

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Gutendex (base URL `https://gutendex.com/`): a JSON index of Project Gutenberg's catalog, hosted
 * apart from gutenberg.org. It is the Gutenberg tab's FALLBACK for search and the popular list
 * while gutenberg.org is down (see `FailoverGutenbergClient`); gutenberg.org stays the primary.
 *
 * Results are sorted by popularity (download count) by default; no [search] = the popular list.
 * Retrofit drops a null query parameter.
 */
interface GutendexApi {

    @GET("books/")
    suspend fun books(
        @Query("search") search: String?,
        @Query("page") page: Int,
    ): GutendexPage
}

// Gson DTOs. Every field is nullable: Gson fills Kotlin classes reflectively (no constructor, so
// no defaults and no null checks), and a field the server leaves out must not become a hidden
// null in a non-null type. Kept from R8 by proguard-rules.pro (data.catalog.gutendex.**).

data class GutendexPage(
    @SerializedName("count") val count: Int?,
    @SerializedName("next") val next: String?,
    @SerializedName("results") val results: List<GutendexBook>?,
)

data class GutendexBook(
    @SerializedName("id") val id: Long?,
    @SerializedName("title") val title: String?,
    @SerializedName("authors") val authors: List<GutendexPerson>?,
    @SerializedName("languages") val languages: List<String>?,
    @SerializedName("copyright") val copyright: Boolean?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("formats") val formats: Map<String, String>?,
)

data class GutendexPerson(
    @SerializedName("name") val name: String?,
)
