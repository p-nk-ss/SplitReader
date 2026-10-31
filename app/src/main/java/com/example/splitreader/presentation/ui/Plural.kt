package com.example.splitreader.presentation.ui

import kotlin.math.roundToInt

/** English-only pluralization for stat labels ("1 word" / "2 words"). Revisit at localization. */
fun plural(count: Int, singular: String): String = if (count == 1) singular else singular + "s"

/** A pane split as the reader's Display dialog shows it — "60 / 40" — rounded the same everywhere. */
fun splitLabel(ratio: Float): String {
    val left = (ratio * 100).roundToInt()
    return "$left / ${100 - left}"
}
