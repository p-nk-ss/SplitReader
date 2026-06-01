package com.example.splitreader.domain.model

enum class TranslationProviderCategory { FREE, ADVANCED }

enum class TranslationProvider(
    val displayName: String,
    val requiresApiKey: Boolean,
    val requiresNetwork: Boolean,
    val category: TranslationProviderCategory,
    val description: String,
    val tracksUsage: Boolean,
) {
    MLKIT(
        displayName = "ML Kit (offline)",
        requiresApiKey = false,
        requiresNetwork = false,
        category = TranslationProviderCategory.FREE,
        description = "On-device translation. Works offline once language packs download.",
        tracksUsage = false,
    ),
    LIBRE_TRANSLATE(
        displayName = "LibreTranslate",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "Open-source engine (Argos). Get a free-tier key at portal.libretranslate.com, or point to a self-hosted instance.",
        tracksUsage = true,
    ),
    GOOGLE_CLOUD(
        displayName = "Google Cloud Translation",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "Official API. 500K characters/month free, paid after.",
        tracksUsage = true,
    ),
    DEEPL(
        displayName = "DeepL",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "DeepL Free API. 500K characters/month free with own key.",
        tracksUsage = true,
    );

    companion object {
        fun fromName(name: String?): TranslationProvider =
            entries.find { it.name == name } ?: MLKIT
    }
}
