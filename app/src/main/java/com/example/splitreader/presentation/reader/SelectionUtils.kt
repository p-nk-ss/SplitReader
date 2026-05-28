package com.example.splitreader.presentation.reader

fun sentenceBoundsAt(text: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
    if (text.isEmpty()) return Pair(0, 0)
    val anchor = selStart.coerceIn(0, text.lastIndex)

    var start = anchor
    while (start > 0) {
        val prev = text[start - 1]
        if (prev == '.' || prev == '!' || prev == '?' || prev == '…' || prev == '\n') break
        start--
    }
    while (start < text.length && text[start].isWhitespace()) start++

    var end = selEnd.coerceAtLeast(anchor + 1).coerceAtMost(text.length)
    while (end < text.length) {
        val ch = text[end]
        if (ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == '\n') {
            end++
            while (end < text.length && (text[end] == '.' || text[end] == '!' || text[end] == '?')) end++
            break
        }
        end++
    }
    return Pair(start, end.coerceAtMost(text.length))
}
