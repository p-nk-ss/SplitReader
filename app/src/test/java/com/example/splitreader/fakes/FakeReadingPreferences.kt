package com.example.splitreader.fakes

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Records the provider, target language, appearance, positions and excerpts the caller writes;
 * every other preference is a constant. The target defaults to RUSSIAN so a reader that detects
 * ENGLISH keeps it (the VM swaps the target only when it equals the detected language).
 * When [log] is given, every provider write appends `"select <PROVIDER>"` to it.
 */
internal class FakeReadingPreferences(
    @Volatile var provider: TranslationProvider = TranslationProvider.QUICK_TRANSLATE,
    private val log: MutableList<String>? = null,
) : ReadingPreferences {
    @get:JvmName("storedTargetLanguage") var targetLanguage = Language.RUSSIAN
    val positions = mutableMapOf<String, ReadingPosition>()
    val excerpts = mutableMapOf<String, String>()

    override fun getLastBookUri(): String? = null
    override fun saveReadingPosition(bookUri: String, position: ReadingPosition) {
        positions[bookUri] = position
    }
    override fun getReadingPosition(bookUri: String) = positions[bookUri] ?: ReadingPosition.START
    override fun saveExcerpt(bookUri: String, text: String) {
        excerpts[bookUri] = text
    }
    override fun getExcerpt(bookUri: String): String? = excerpts[bookUri]
    override fun markFinished(bookUri: String) = Unit
    override fun isFinished(bookUri: String) = false
    override fun clearProgress(bookUri: String) = Unit
    override fun saveTargetLanguage(language: Language) {
        targetLanguage = language
    }
    override fun getTargetLanguage() = targetLanguage
    override fun savePortraitHintDismissed(dismissed: Boolean) = Unit
    override fun getPortraitHintDismissed() = true
    override fun setTranslatorProvider(provider: TranslationProvider) {
        log?.add("select $provider")
        this.provider = provider
    }
    override fun getTranslatorProvider() = provider
    override fun saveTtsRate(rate: Float) = Unit
    override fun getTtsRate() = 1f
    override fun saveTtsPitch(pitch: Float) = Unit
    override fun getTtsPitch() = 1f

    private val _appearance = MutableStateFlow(ReadingAppearance.defaults(false))
    override val appearance: StateFlow<ReadingAppearance> = _appearance

    val appearanceUpdates = mutableListOf<ReadingAppearance>()

    override fun updateAppearance(transform: (ReadingAppearance) -> ReadingAppearance) {
        _appearance.update { transform(it).sanitized() }
        appearanceUpdates += _appearance.value
    }
}
