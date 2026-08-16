package com.example.splitreader.presentation.ui

/** English-only pluralization for stat labels ("1 word" / "2 words"). Revisit at localization. */
fun plural(count: Int, singular: String): String = if (count == 1) singular else singular + "s"
