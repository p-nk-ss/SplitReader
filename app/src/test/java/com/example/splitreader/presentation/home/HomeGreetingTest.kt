package com.example.splitreader.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeGreetingTest {

    @Test
    fun `eyebrow is upper-case day, day-of-month and short month`() {
        assertEquals("SUNDAY · 2 AUG", formatDateEyebrow(LocalDate.of(2026, 8, 2)))
    }

    @Test
    fun `eyebrow does not zero-pad the day of month`() {
        assertEquals("FRIDAY · 1 MAY", formatDateEyebrow(LocalDate.of(2026, 5, 1)))
    }

    @Test
    fun `eyebrow uses English regardless of default locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            assertEquals("SUNDAY · 2 AUG", formatDateEyebrow(LocalDate.of(2026, 8, 2)))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `morning runs until noon`() {
        assertEquals("Good morning, Alex", formatGreeting(hour = 0, userName = "Alex"))
        assertEquals("Good morning, Alex", formatGreeting(hour = 11, userName = "Alex"))
    }

    @Test
    fun `afternoon runs from noon to five`() {
        assertEquals("Good afternoon, Alex", formatGreeting(hour = 12, userName = "Alex"))
        assertEquals("Good afternoon, Alex", formatGreeting(hour = 16, userName = "Alex"))
    }

    @Test
    fun `evening runs from five`() {
        assertEquals("Good evening, Alex", formatGreeting(hour = 17, userName = "Alex"))
        assertEquals("Good evening, Alex", formatGreeting(hour = 23, userName = "Alex"))
    }

    @Test
    fun `a null or blank name leaves the greeting bare`() {
        assertEquals("Good morning", formatGreeting(hour = 9, userName = null))
        assertEquals("Good morning", formatGreeting(hour = 9, userName = "   "))
    }
}
