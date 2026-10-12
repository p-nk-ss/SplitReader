package com.example.splitreader.presentation

import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReaderThemeKey
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.presentation.reader.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The app-wide theme and orientation are projections of the single `ReadingPreferences.appearance`. */
class AppThemeViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `themeKey follows appearance theme`() = runTest(main.dispatcher) {
        val prefs = FakeReadingPreferences()
        val vm = AppThemeViewModel(prefs)
        runCurrent()

        prefs.updateAppearance { it.copy(theme = ReaderThemeKey.NIGHT) }
        runCurrent()

        assertEquals(ReaderThemeKey.NIGHT, vm.themeKey.value)
    }

    @Test
    fun `orientationLock follows appearance orientationLock`() = runTest(main.dispatcher) {
        val prefs = FakeReadingPreferences()
        val vm = AppThemeViewModel(prefs)
        runCurrent()

        prefs.updateAppearance { it.copy(orientationLock = OrientationLock.LANDSCAPE) }
        runCurrent()

        assertEquals(OrientationLock.LANDSCAPE, vm.orientationLock.value)
    }

    /**
     * Unfalsifiable by the `distinctUntilChanged` break the plan names: `themeKey` is a StateFlow,
     * and a StateFlow never re-emits a value equal to its current one, so dropping
     * `distinctUntilChanged` leaves this green (observed). It pins the observable contract —
     * `MainActivity` re-applies the theme only when the theme changes — against `themeKey` ever
     * becoming a non-conflating flow.
     */
    @Test
    fun `a font change does not re-emit the theme (StateFlow conflation)`() = runTest(main.dispatcher) {
        val prefs = FakeReadingPreferences()
        val vm = AppThemeViewModel(prefs)
        val emissions = mutableListOf<ReaderThemeKey>()
        backgroundScope.launch { vm.themeKey.collect { emissions += it } }
        runCurrent()

        prefs.updateAppearance { it.copy(textSize = it.textSize + 2f) }
        runCurrent()

        assertEquals(listOf(ReaderThemeKey.PAPER), emissions)
    }
}
