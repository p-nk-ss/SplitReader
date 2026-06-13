package com.example.splitreader.domain.model

/**
 * A free public-domain book discoverable in the catalog. Only books that expose a DRM-free EPUB
 * ([epubUrl]) are surfaced — they flow through the existing EpubParser once downloaded.
 *
 * [id] is the source-local stable identifier: a numeric ebook id for Project Gutenberg (e.g. "1661")
 * or an author/title slug for Standard Ebooks (e.g. "charles-dickens/a-christmas-carol"). It is
 * unique only within a [source], so list keys and download paths combine the two.
 */
data class CatalogBook(
    val source: CatalogSource,
    val id: String,
    val title: String,
    val author: String,
    val languages: List<String>,
    val coverUrl: String?,
    val epubUrl: String,
)

/** One page of catalog results, with a flag for whether further pages exist. */
data class CatalogPage(
    val books: List<CatalogBook>,
    val hasNext: Boolean,
)
