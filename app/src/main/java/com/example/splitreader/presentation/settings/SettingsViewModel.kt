package com.example.splitreader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.repository.SpeechSynthesizer
import com.example.splitreader.domain.repository.TranslatorSettings
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReadingDefaults
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.EntitlementRepository
import com.example.splitreader.domain.translator.InstalledPack
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineModelStore
import com.example.splitreader.domain.model.TranslatorConfigState
import com.example.splitreader.domain.model.ReaderThemeKey
import com.example.splitreader.domain.model.ReadingFont
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aggregate of all globally-persisted preferences, surfaced by the Settings screen. */
data class SettingsUiState(
    // Appearance
    val readerTheme: ReaderThemeKey = ReaderThemeKey.PAPER,
    val targetLanguage: Language = Language.ENGLISH,
    val splitRatio: Float = ReadingDefaults.SPLIT_RATIO,
    val showTranslation: Boolean = ReadingDefaults.SHOW_TRANSLATION,
    val showIllustrations: Boolean = ReadingDefaults.SHOW_ILLUSTRATIONS,
    val horizontalMargin: Float = ReadingDefaults.HORIZONTAL_MARGIN,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    // Typography
    val readingFont: ReadingFont = ReadingFont.SERIF,
    val textSize: Float = ReadingDefaults.TEXT_SIZE,
    val lineHeightMultiplier: Float = ReadingDefaults.LINE_HEIGHT,
    val letterSpacing: Float = ReadingDefaults.LETTER_SPACING,
    val textIndent: Float = ReadingDefaults.TEXT_INDENT,
    val paragraphSpacing: Float = ReadingDefaults.PARAGRAPH_SPACING,
    val justifyText: Boolean = ReadingDefaults.JUSTIFY_TEXT,
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
        readerTheme = when (progressManager.getReaderThemeName()) {
            "DEFAULT" -> ReaderThemeKey.PAPER
            else -> ReaderThemeKey.entries.find { it.name == progressManager.getReaderThemeName() }
                ?: ReaderThemeKey.PAPER
        },
        targetLanguage = progressManager.getTargetLanguage(),
        splitRatio = progressManager.getSplitRatio(),
        showTranslation = progressManager.getShowTranslation(),
        showIllustrations = progressManager.getShowIllustrations(),
        horizontalMargin = progressManager.getHorizontalMargin(),
        orientationLock = progressManager.getOrientationLock(),
        readingFont = ReadingFont.entries.find { it.name == progressManager.getReadingFontName() }
            ?: ReadingFont.SERIF,
        textSize = progressManager.getTextSize(),
        lineHeightMultiplier = progressManager.getLineHeightMultiplier(),
        letterSpacing = progressManager.getLetterSpacing(),
        textIndent = progressManager.getTextIndent(),
        paragraphSpacing = progressManager.getParagraphSpacing(),
        justifyText = progressManager.getJustifyText(),
        translatorProvider = translatorSettings.config.value.current,
        translatorConfig = translatorSettings.config.value,
        ttsRate = progressManager.getTtsRate(),
        ttsPitch = progressManager.getTtsPitch(),
    )

    // ── Appearance ─────────────────────────────────────────────────────────────

    fun setReaderTheme(theme: ReaderThemeKey) {
        progressManager.saveReaderTheme(theme.name)
        _state.update { it.copy(readerTheme = theme) }
    }

    fun setTargetLanguage(lang: Language) {
        progressManager.saveTargetLanguage(lang)
        _state.update { it.copy(targetLanguage = lang) }
    }

    fun setSplitRatio(ratio: Float) {
        val clamped = ratio.coerceIn(ReadingDefaults.SPLIT_RATIO_RANGE)
        progressManager.saveSplitRatio(clamped)
        _state.update { it.copy(splitRatio = clamped) }
    }

    fun setShowTranslation(show: Boolean) {
        progressManager.saveShowTranslation(show)
        _state.update { it.copy(showTranslation = show) }
    }

    fun setShowIllustrations(show: Boolean) {
        progressManager.saveShowIllustrations(show)
        _state.update { it.copy(showIllustrations = show) }
    }

    fun setHorizontalMargin(margin: Float) {
        val clamped = margin.coerceIn(ReadingDefaults.HORIZONTAL_MARGIN_RANGE)
        progressManager.saveHorizontalMargin(clamped)
        _state.update { it.copy(horizontalMargin = clamped) }
    }

    fun setOrientationLock(lock: OrientationLock) {
        progressManager.saveOrientationLock(lock)
        _state.update { it.copy(orientationLock = lock) }
    }

    // ── Typography ───────────────────────────────────────────────────────────

    fun setReadingFont(font: ReadingFont) {
        progressManager.saveReadingFont(font.name)
        _state.update { it.copy(readingFont = font) }
    }

    fun setTextSize(size: Float) {
        val clamped = size.coerceIn(ReadingDefaults.TEXT_SIZE_RANGE)
        progressManager.saveTextSize(clamped)
        _state.update { it.copy(textSize = clamped) }
    }

    fun setLineHeight(multiplier: Float) {
        val clamped = multiplier.coerceIn(ReadingDefaults.LINE_HEIGHT_RANGE)
        progressManager.saveLineHeightMultiplier(clamped)
        _state.update { it.copy(lineHeightMultiplier = clamped) }
    }

    fun setLetterSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(ReadingDefaults.LETTER_SPACING_RANGE)
        progressManager.saveLetterSpacing(clamped)
        _state.update { it.copy(letterSpacing = clamped) }
    }

    fun setTextIndent(indent: Float) {
        val clamped = indent.coerceIn(ReadingDefaults.TEXT_INDENT_RANGE)
        progressManager.saveTextIndent(clamped)
        _state.update { it.copy(textIndent = clamped) }
    }

    fun setParagraphSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(ReadingDefaults.PARAGRAPH_SPACING_RANGE)
        progressManager.saveParagraphSpacing(clamped)
        _state.update { it.copy(paragraphSpacing = clamped) }
    }

    fun setJustifyText(justify: Boolean) {
        progressManager.saveJustifyText(justify)
        _state.update { it.copy(justifyText = justify) }
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
