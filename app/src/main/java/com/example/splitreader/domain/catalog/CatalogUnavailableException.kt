package com.example.splitreader.domain.catalog

import com.example.splitreader.domain.model.CatalogSource
import java.io.IOException

/**
 * A catalog source answered with a server error (HTTP 5xx) — e.g. Project Gutenberg's maintenance
 * page (503 + Retry-After). Raised by the data layer for search and book download, so the screen
 * shows [message] instead of a raw "HTTP 503".
 *
 * The message is the same for every source and names none: the Gutenberg tab is labelled "Open
 * Library", which is also a real, separate service (openlibrary.org) that may well be up, and the
 * Project Gutenberg name is kept out of the UI. [source] is for diagnostics only.
 */
class CatalogUnavailableException(val source: CatalogSource) :
    IOException("This catalog is temporarily unavailable. Try again later.")
