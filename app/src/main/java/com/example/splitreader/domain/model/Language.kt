package com.example.splitreader.domain.model

import com.google.mlkit.nl.translate.TranslateLanguage

// Endonyms (the language's own name) are intentional in the language picker;
// flag emoji were removed because a flag denotes a country, not a language.
// Enum order is the picker grid order (3 columns): keep it laid out as the rows below.
enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    UKRAINIAN("uk", "Українська"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    DUTCH("nl", "Nederlands"),
    POLISH("pl", "Polski"),
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    ARABIC("ar", "العربية"),
    HINDI("hi", "हिन्दी"),
    TURKISH("tr", "Türkçe"),
    SWEDISH("sv", "Svenska"),
    CZECH("cs", "Čeština"),
    RUSSIAN("ru", "Русский");

    companion object {
        fun fromCode(code: String): Language = entries.find { it.code == code } ?: ENGLISH
    }
}

fun Language.toTranslateLanguage(): String = when (this) {
    Language.ENGLISH -> TranslateLanguage.ENGLISH
    Language.UKRAINIAN -> TranslateLanguage.UKRAINIAN
    Language.GERMAN -> TranslateLanguage.GERMAN
    Language.FRENCH -> TranslateLanguage.FRENCH
    Language.SPANISH -> TranslateLanguage.SPANISH
    Language.ITALIAN -> TranslateLanguage.ITALIAN
    Language.PORTUGUESE -> TranslateLanguage.PORTUGUESE
    Language.DUTCH -> TranslateLanguage.DUTCH
    Language.POLISH -> TranslateLanguage.POLISH
    Language.CHINESE -> TranslateLanguage.CHINESE
    Language.JAPANESE -> TranslateLanguage.JAPANESE
    Language.KOREAN -> TranslateLanguage.KOREAN
    Language.ARABIC -> TranslateLanguage.ARABIC
    Language.HINDI -> TranslateLanguage.HINDI
    Language.TURKISH -> TranslateLanguage.TURKISH
    Language.SWEDISH -> TranslateLanguage.SWEDISH
    Language.CZECH -> TranslateLanguage.CZECH
    Language.RUSSIAN -> TranslateLanguage.RUSSIAN
}
