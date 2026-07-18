package com.example.splitreader.domain.repository

/** On-device text-to-speech: speaks text and adjusts rate/pitch. */
interface SpeechSynthesizer {
    fun speak(text: String, langCode: String)
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    fun shutdown()
}
