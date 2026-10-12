package com.example.splitreader.presentation.settings

import com.example.splitreader.domain.model.ProviderConfig
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

/** SettingsViewModel's translator section is a thin delegate to the shared TranslatorSettings. */
class SettingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeTranslatorSettings(current = TranslationProvider.QUICK_TRANSLATE)

    private fun vm() = SettingsViewModel(
        progressManager = FakeReadingPreferences(),
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
