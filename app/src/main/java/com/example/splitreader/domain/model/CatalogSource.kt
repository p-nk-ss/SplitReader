package com.example.splitreader.domain.model

/** A free public-domain book catalog the user can browse, selectable via tabs on the catalog screen. */
enum class CatalogSource(val displayName: String) {
    // Display label only; the backing source/feed is Project Gutenberg (kept out of the UI).
    GUTENBERG("Open Library"),
    STANDARD_EBOOKS("Standard Ebooks"),
}
