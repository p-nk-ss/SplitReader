package com.example.splitreader.data.catalog

import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogSource

/** The service's own name, for "… is temporarily unavailable" (not the tab label). */
private fun CatalogSource.serviceName(): String = when (this) {
    CatalogSource.GUTENBERG -> "Project Gutenberg"
    CatalogSource.STANDARD_EBOOKS -> "Standard Ebooks"
}

/**
 * Throws [CatalogUnavailableException] when [source] answered [httpCode] 5xx (down / maintenance);
 * any other code is left to the caller's own error.
 */
internal fun throwIfUnavailable(source: CatalogSource, httpCode: Int) {
    if (httpCode in 500..599) throw CatalogUnavailableException(source.serviceName())
}
