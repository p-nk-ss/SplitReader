package com.example.splitreader.domain.parser.util

import com.example.splitreader.domain.parser.BookParser

/**
 * Picks the highest-[BookParser.priority] parser whose [BookParser.canParse] matches. The registry is
 * a Hilt Set with no guaranteed iteration order, and canParse predicates overlap (e.g. FB2 claims
 * generic text/xml), so priority — with a stable class-name tie-break — makes the choice deterministic.
 * A final [System.identityHashCode] key breaks ties between same-class instances so the result never
 * depends on the input collection's iteration order.
 */
fun selectParser(
    parsers: Collection<BookParser>,
    fileName: String,
    mimeType: String,
    header: ByteArray,
): BookParser? =
    parsers
        .filter { it.canParse(fileName, mimeType, header) }
        .maxWithOrNull(
            compareBy(
                { it.priority },
                { it::class.java.name },
                { System.identityHashCode(it) },
            )
        )
