package com.example.splitreader.domain.catalog

import java.io.IOException

/**
 * A catalog source answered with a server error (HTTP 5xx) — e.g. Project Gutenberg's maintenance
 * page (503 + Retry-After). Raised by the data layer for search and book download, so the screen
 * shows [message] instead of a raw "HTTP 503". [sourceName] is the service's name as the user
 * should read it.
 */
class CatalogUnavailableException(val sourceName: String) :
    IOException("$sourceName is temporarily unavailable. Try again later.")
