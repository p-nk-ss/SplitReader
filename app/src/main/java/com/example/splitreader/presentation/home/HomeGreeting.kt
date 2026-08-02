package com.example.splitreader.presentation.home

import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

/**
 * "SUNDAY · 2 AUG" — the eyebrow above the Home greeting.
 *
 * Locale is pinned to English deliberately: the UI is English-only for now (full localization is a
 * 1.1 item), and leaving it on the default locale would make the string, and therefore the Home
 * goldens, depend on the machine running the tests.
 */
fun formatDateEyebrow(date: LocalDate): String {
    val dayName = date.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)
    val monthName = date.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH)
    return "$dayName · ${date.dayOfMonth} $monthName"
}

/**
 * "Good morning, Alex", or just "Good morning" when there is no name.
 *
 * Takes the hour rather than reading the clock so it is unit-testable and so the Home goldens stop
 * depending on when they were recorded — the greeting is 28sp, so a change here reflows the line
 * rather than nudging a few pixels.
 */
fun formatGreeting(hour: Int, userName: String?): String {
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (!userName.isNullOrBlank()) "$greeting, $userName" else greeting
}
