package com.example.splitreader.domain.model.stats

/** Aggregated reading minutes for a single calendar day (yyyy-MM-dd). */
data class DailyMinutes(val day: String, val minutes: Int)

/** Aggregated reading minutes for a single book title. */
data class BookMinutes(val title: String, val minutes: Int)

/** Aggregated reading minutes for a single source language. */
data class LangMinutes(val lang: String, val minutes: Int)
