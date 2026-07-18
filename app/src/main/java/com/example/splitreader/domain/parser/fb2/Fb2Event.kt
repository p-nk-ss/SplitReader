package com.example.splitreader.domain.parser.fb2

/** A namespace-agnostic FB2 XML event. The adapter resolves [href] (any *href attr, '#' stripped)
 *  and [id] so the builder stays free of parser/attribute concerns. */
sealed interface Fb2Event {
    data class Start(val name: String, val href: String? = null, val id: String? = null) : Fb2Event
    data class Text(val text: String) : Fb2Event
    data class End(val name: String) : Fb2Event
}
