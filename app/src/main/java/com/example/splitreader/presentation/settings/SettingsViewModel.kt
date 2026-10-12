package com.example.splitreader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.repository.SpeechSynthesizer
import com.example.splitreader.domain.repository.TranslatorSettings
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.EntitlementRepository
import com.example.splitreader.domain.translator.InstalledPack
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineModelStore
import com.example.splitreader.domain.model.TranslatorConfigState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aggregate of all globally-persisted preferences, surfaced by the Settings screen. */
data class SettingsUiState(
    // Reading appearance (theme, typography, layout, toggles, orientation)
    val appearance: ReadingAppearance = ReadingAppearance.defaults(isTablet = false),
    val targetLanguage: Language = Language.ENGLISH,
    // Translation engine
    val translatorProvider: TranslationProvider = TranslationProvider.MLKIT,
    val translatorConfig: TranslatorConfigState =
        TranslatorConfigState(current = TranslationProvider.MLKIT, configs = emptyMap()),
    // Storage
    val cachedTranslationCount: Int = 0,
    // Read aloud
    val ttsRate: Float = ReadingDefaults.TTS_RATE,
    val ttsPitch: Float = ReadingDefaults.TTS_PITCH,
    // Entitlement (debug-only toggle in the UI)
    val isPremium: Boolean = false,
    // Language packs (Offline HQ)
    val languagePacks: List<InstalledPack> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val progressManager: ReadingPreferences,
    private val translatorSettings: TranslatorSettings,
    private val translationRepository: TranslationRepository,
    private val textToSpeechManager: SpeechSynthesizer,
    private val entitlementRepository: EntitlementRepository,
    private val offlineModelStore: OfflineModelStore,
) : ViewModel() {

    private val _state = MutableStateFlow(loadState())
    val state = _state.asStateFlow()

    init {
        refreshCacheCount()
        // loadState() seeds a cheap prefs-only placeholder; refresh() builds the real config off the main thread.
        viewModelScope.launch { translatorSettings.refresh() }
        viewModelScope.launch {
            translatorSettings.config.collect { cfg ->
                _state.update { it.copy(translatorProvider = cfg.current, translatorConfig = cfg) }
            }
        }
        viewModelScope.launch {
            progressManager.appearance.collect { a -> _state.update { it.copy(appearance = a) } }
        }
        viewModelScope.launch {
            entitlementRepository.isPremium.collect { premium ->
                _state.update { it.copy(isPremium = premium) }
            }
        }
        viewModelScope.launch {
            offlineModelStore.installed().collect { packs -> _state.update { it.copy(languagePacks = packs) } }
        }
    }

    /** Debug-only: flip premium to test the unlocked (unlimited-library) state. */
    fun setPremiumDebug(premium: Boolean) {
        entitlementRepository.setPremium(premium)
    }

    private fun loadState(): SettingsUiState = SettingsUiState(
        appearance = progressManager.appearance.value,
        targetLanguage = progressManager.getTargetLanguage(),
        translatorProvider = translatorSettings.config.value.current,
        translatorConfig = translatorSettings.config.value,
        ttsRate = progressManager.getTtsRate(),
        ttsPitch = progressManager.getTtsPitch(),
    )

    // ── Appearance ─────────────────────────────────────────────────────────────

    /** Persists [transform] of the current appearance; the `appearance` collector updates [state]. */
    fun updateAppearance(transform: (ReadingAppearance) -> ReadingAppearance) =
        progressManager.updateAppearance(transform)

    fun setTargetLanguage(lang: Language) {
        progressManager.saveTargetLanguage(lang)
        _state.update { it.copy(targetLanguage = lang) }
    }

    // ── Translation engine ─────────────────────────────────────────────────────

    fun selectProvider(provider: TranslationProvider) {
        viewModelScope.launch { translatorSettings.select(provider) }
    }

    /** Saves the key/endpoint and selects [provider]. */
    fun configureProvider(provider: TranslationProvider, key: String?, secondary: String?) {
        viewModelScope.launch { translatorSettings.saveKey(provider, key, secondary) }
    }

    fun clearProvider(provider: TranslationProvider) {
        viewModelScope.launch { translatorSettings.clear(provider) }
    }

    fun refreshTranslationUsage() {
        viewModelScope.launch { translatorSettings.refresh() }
    }

    fun resetTranslationUsage(provider: TranslationProvider) {
        viewModelScope.launch { translatorSettings.resetUsage(provider) }
    }

    fun deleteLanguagePack(pair: ModelPair) {
        viewModelScope.launch(Dispatchers.IO) { offlineModelStore.delete(pair) }
    }

    // ── Storage ─────────────────────────────────────────────────────────────────

    fun refreshCacheCount() {
        viewModelScope.launch {
            val count = translationRepository.cachedCount()
            _state.update { it.copy(cachedTranslationCount = count) }
        }
    }

    fun clearTranslationCache() {
        viewModelScope.launch {
            translationRepository.clearCache()
            _state.update { it.copy(cachedTranslationCount = 0) }
        }
    }

    // ── Read aloud ────────────────────────────────────────────────────────────

    fun setTtsRate(rate: Float) {
        val clamped = rate.coerceIn(ReadingDefaults.TTS_RATE_RANGE)
        textToSpeechManager.setRate(clamped)
        _state.update { it.copy(ttsRate = clamped) }
    }

    fun setTtsPitch(pitch: Float) {
        val clamped = pitch.coerceIn(ReadingDefaults.TTS_PITCH_RANGE)
        textToSpeechManager.setPitch(clamped)
        _state.update { it.copy(ttsPitch = clamped) }
    }

    fun testVoice() {
        val lang = _state.value.targetLanguage
        textToSpeechManager.speak(VOICE_SAMPLE[lang] ?: VOICE_SAMPLE.getValue(Language.ENGLISH), lang.code)
    }

    private companion object {
        val VOICE_SAMPLE: Map<Language, String> = mapOf(
            Language.ENGLISH to "The quick brown fox jumps over the lazy dog.",
            Language.RUSSIAN to "Съешь же ещё этих мягких французских булочек.",
        )
    }
}
