package com.example.splitreader.presentation.settings

import com.example.splitreader.domain.model.ProviderConfig
import com.example.splitreader.domain.model.ReaderThemeKey
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslatorConfigState
import com.example.splitreader.fakes.FakeEntitlementRepository
import com.example.splitreader.fakes.FakeOfflineModelStore
import com.example.splitreader.fakes.FakeReadingPreferences
import com.example.splitreader.fakes.FakeSpeechSynthesizer
import com.example.splitreader.fakes.FakeTranslationRepository
import com.example.splitreader.fakes.FakeTranslatorSettings
import com.example.splitreader.presentation.reader.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** SettingsViewModel's translator and appearance sections are thin delegates to the shared ports. */
class SettingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE)

    private fun vm(prefs: FakeReadingPreferences = FakeReadingPreferences()) = SettingsViewModel(
        progressManager = prefs,
        translatorSettings = settings,
        translationRepository = FakeTranslationRepository(),
        textToSpeechManager = FakeSpeechSynthesizer(),
        entitlementRepository = FakeEntitlementRepository(),
        offlineModelStore = FakeOfflineModelStore(),
    )

    @Test
    fun `translator commands are delegated`() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        settings.log.clear()

        vm.selectProvider(TranslationProvider.AZURE)
        vm.configureProvider(TranslationProvider.DEEPL, "k", null)
        vm.clearProvider(TranslationProvider.DEEPL)
        vm.resetTranslationUsage(TranslationProvider.AZURE)
        advanceUntilIdle()

        assertEquals(
            listOf("select AZURE", "saveKey DEEPL", "clear DEEPL", "resetUsage AZURE"),
            settings.log.toList(),
        )
    }

    @Test
    fun `updateAppearance reaches prefs and UI state`() = runTest(main.dispatcher) {
        val prefs = FakeReadingPreferences()
        val vm = vm(prefs)
        advanceUntilIdle()

        vm.updateAppearance { it.copy(horizontalMargin = 20f, theme = ReaderThemeKey.SEPIA) }
        advanceUntilIdle()

        assertEquals(20f, prefs.appearance.value.horizontalMargin, 0f)
        assertEquals(ReaderThemeKey.SEPIA, prefs.appearance.value.theme)
        assertEquals(prefs.appearance.value, vm.state.value.appearance)
    }

    @Test
    fun `config reaches the UI state`() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        val next = TranslatorConfigState(
            current = TranslationProvider.DEEPL,
            configs = mapOf(TranslationProvider.DEEPL to ProviderConfig(configured = true)),
        )

        settings.mutableConfig.value = next
        advanceUntilIdle()

        assertEquals(next, vm.state.value.translatorConfig)
        assertEquals(TranslationProvider.DEEPL, vm.state.value.translatorProvider)
    }
}
