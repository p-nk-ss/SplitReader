package com.example.splitreader.domain.model

import com.google.mlkit.nl.translate.TranslateLanguage

enum class Language(val code: String, val displayName: String, val flag: String) {
    ENGLISH("en", "English", "🇬🇧"),
    RUSSIAN("ru", "Русский", "🇷🇺"),
    GERMAN("de", "Deutsch", "🇩🇪"),
    FRENCH("fr", "Français", "🇫🇷"),
    SPANISH("es", "Español", "🇪🇸"),
    ITALIAN("it", "Italiano", "🇮🇹"),
    CHINESE("zh", "中文", "🇨🇳"),
    JAPANESE("ja", "日本語", "🇯🇵"),
    PORTUGUESE("pt", "Português", "🇵🇹"),
    ARABIC("ar", "العربية", "🇸🇦"),
    KOREAN("ko", "한국어", "🇰🇷"),
    TURKISH("tr", "Türkçe", "🇹🇷");

    companion object {
        fun fromCode(code: String): Language = entries.find { it.code == code } ?: ENGLISH
    }
}

fun Language.toTranslateLanguage(): String = when (this) {
    Language.ENGLISH -> TranslateLanguage.ENGLISH
    Language.RUSSIAN -> TranslateLanguage.RUSSIAN
    Language.GERMAN -> TranslateLanguage.GERMAN
    Language.FRENCH -> TranslateLanguage.FRENCH
    Language.SPANISH -> TranslateLanguage.SPANISH
    Language.ITALIAN -> TranslateLanguage.ITALIAN
    Language.CHINESE -> TranslateLanguage.CHINESE
    Language.JAPANESE -> TranslateLanguage.JAPANESE
    Language.PORTUGUESE -> TranslateLanguage.PORTUGUESE
    Language.ARABIC -> TranslateLanguage.ARABIC
    Language.KOREAN -> TranslateLanguage.KOREAN
    Language.TURKISH -> TranslateLanguage.TURKISH
}
