package com.example.splitreader.fakes

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReadingAppearance
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Records the provider, target language, pane visibility, positions and excerpts the caller writes;
 * every other preference is a constant. The target defaults to RUSSIAN so a reader that detects
 * ENGLISH keeps it (the VM swaps the target only when it equals the detected language).
 * When [log] is given, every provider write appends `"select <PROVIDER>"` to it.
 */
internal class FakeReadingPreferences(
    @Volatile var provider: TranslationProvider = TranslationProvider.QUICK_TRANSLATE,
    private val log: MutableList<String>? = null,
) : ReadingPreferences {
    @get:JvmName("storedTargetLanguage") var targetLanguage = Language.RUSSIAN
    @get:JvmName("storedShowTranslation") var showTranslation = true
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
    override fun saveNavigationSideLeft(isLeft: Boolean) = Unit
    override fun isNavigationLeft() = false
    override val readerThemeName: StateFlow<String> = MutableStateFlow("paper")
    override fun saveReaderTheme(themeName: String) = Unit
    override fun getReaderThemeName() = "paper"
    override val orientationLock: StateFlow<OrientationLock> = MutableStateFlow(OrientationLock.AUTO)
    override fun saveOrientationLock(lock: OrientationLock) = Unit
    override fun getOrientationLock() = OrientationLock.AUTO
    override fun saveLineHeightMultiplier(multiplier: Float) = Unit
    override fun getLineHeightMultiplier() = 1f
    override fun saveSplitRatio(ratio: Float) = Unit
    override fun getSplitRatio() = 0.5f
    override fun saveVerticalSplitRatio(ratio: Float) = Unit
    override fun getVerticalSplitRatio() = 0.5f
    override fun savePortraitHintDismissed(dismissed: Boolean) = Unit
    override fun getPortraitHintDismissed() = true
    override fun saveShowTranslation(show: Boolean) {
        showTranslation = show
    }
    override fun getShowTranslation() = showTranslation
    override fun saveShowIllustrations(show: Boolean) = Unit
    override fun getShowIllustrations() = true
    override fun saveHorizontalMargin(margin: Float) = Unit
    override fun getHorizontalMargin() = 0f
    override fun setTranslatorProvider(provider: TranslationProvider) {
        log?.add("select $provider")
        this.provider = provider
    }
    override fun getTranslatorProvider() = provider
    override fun saveTextSize(size: Float) = Unit
    override fun getTextSize() = 16f
    override fun saveReadingFont(name: String) = Unit
    override fun getReadingFontName() = ""
    override fun saveParagraphSpacing(spacing: Float) = Unit
    override fun getParagraphSpacing() = 0f
    override fun saveLetterSpacing(spacing: Float) = Unit
    override fun getLetterSpacing() = 0f
    override fun saveTextIndent(indent: Float) = Unit
    override fun getTextIndent() = 0f
    override fun saveJustifyText(justify: Boolean) = Unit
    override fun getJustifyText() = false
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
