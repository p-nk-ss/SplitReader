package com.example.splitreader.domain.model

/**
 * A free public-domain book discoverable in the catalog (Project Gutenberg). Only books that
 * expose a DRM-free EPUB ([epubUrl] non-null) are surfaced — they flow through the existing
 * EpubParser once downloaded.
 */
data class CatalogBook(
    val id: Int,
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
