package com.example.splitreader.fakes

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.ReadingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything defaulted; only the translator-provider choice matters to most callers. */
internal class FakeReadingPreferences(
    var provider: TranslationProvider = TranslationProvider.QUICK_TRANSLATE,
) : ReadingPreferences {
    override fun getLastBookUri(): String? = null
    override fun saveReadingPosition(bookUri: String, position: ReadingPosition) = Unit
    override fun getReadingPosition(bookUri: String) = ReadingPosition.START
    override fun saveExcerpt(bookUri: String, text: String) = Unit
    override fun getExcerpt(bookUri: String): String? = null
    override fun markFinished(bookUri: String) = Unit
    override fun isFinished(bookUri: String) = false
    override fun clearProgress(bookUri: String) = Unit
    override fun saveTargetLanguage(language: Language) = Unit
    override fun getTargetLanguage() = Language.ENGLISH
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
    override fun saveShowTranslation(show: Boolean) = Unit
    override fun getShowTranslation() = true
    override fun saveShowIllustrations(show: Boolean) = Unit
    override fun getShowIllustrations() = true
    override fun saveHorizontalMargin(margin: Float) = Unit
    override fun getHorizontalMargin() = 0f
    override fun setTranslatorProvider(provider: TranslationProvider) {
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
}
