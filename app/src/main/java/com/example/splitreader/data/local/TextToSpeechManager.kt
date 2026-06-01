package com.example.splitreader.data.local

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped wrapper around Android's on-device [TextToSpeech] engine.
 *
 * Initialization is asynchronous; a [speak] call made before the engine is ready is queued
 * and played once initialization completes. The engine is created lazily on first use and can be
 * released via [shutdown]; a subsequent [speak] re-initializes it.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context,
    private val progressManager: ReadingProgressManager,
) {
    private val appContext = context.applicationContext
    private var ready = false
    private var pending: Pair<String, String>? = null
    private var tts: TextToSpeech? = null

    private fun ensureEngine(): TextToSpeech =
        tts ?: TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyRateAndPitch()
                pending?.let { (text, langCode) -> play(text, langCode) }
            }
            pending = null
        }.also { tts = it }

    /** Speaks [text] using the voice matching [langCode] (an ISO code such as "en", "fr", "ru"). */
    fun speak(text: String, langCode: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        ensureEngine()
        if (!ready) {
            pending = trimmed to langCode
            return
        }
        play(trimmed, langCode)
    }

    /** Persists and immediately applies the speech rate (1.0 = normal). */
    fun setRate(rate: Float) {
        progressManager.saveTtsRate(rate)
        if (ready) tts?.setSpeechRate(rate)
    }

    /** Persists and immediately applies the voice pitch (1.0 = normal). */
    fun setPitch(pitch: Float) {
        progressManager.saveTtsPitch(pitch)
        if (ready) tts?.setPitch(pitch)
    }

    /** Releases the underlying engine. Safe to call repeatedly; a later [speak] re-initializes it. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }

    private fun applyRateAndPitch() {
        val engine = tts ?: return
        engine.setSpeechRate(progressManager.getTtsRate())
        engine.setPitch(progressManager.getTtsPitch())
    }

    private fun play(text: String, langCode: String) {
        val engine = tts ?: return
        applyRateAndPitch()
        val locale = Locale.forLanguageTag(langCode)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.ENGLISH)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "splitreader-tts")
    }
}
