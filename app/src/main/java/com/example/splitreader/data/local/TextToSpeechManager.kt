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
 * and played once initialization completes.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private var ready = false
    private var pending: Pair<String, String>? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pending?.let { (text, langCode) -> play(text, langCode) }
        }
        pending = null
    }

    /** Speaks [text] using the voice matching [langCode] (an ISO code such as "en", "fr", "ru"). */
    fun speak(text: String, langCode: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (!ready) {
            pending = trimmed to langCode
            return
        }
        play(trimmed, langCode)
    }

    private fun play(text: String, langCode: String) {
        val locale = Locale.forLanguageTag(langCode)
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "splitreader-tts")
    }
}
