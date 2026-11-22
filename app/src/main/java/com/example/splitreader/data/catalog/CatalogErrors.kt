package com.example.splitreader.data.catalog

import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogSource

/**
 * Throws [CatalogUnavailableException] when [source] answered [httpCode] 5xx (down / maintenance);
 * any other code is left to the caller's own error. The message is neutral and names no source
 * (see [CatalogUnavailableException]).
 */
internal fun throwIfUnavailable(source: CatalogSource, httpCode: Int) {
    if (httpCode in 500..599) throw CatalogUnavailableException(source)
}
