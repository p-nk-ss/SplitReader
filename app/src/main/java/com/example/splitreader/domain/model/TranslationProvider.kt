package com.example.splitreader.domain.model

enum class TranslationProviderCategory { FREE, ADVANCED }

enum class TranslationProvider(
    val displayName: String,
    val requiresApiKey: Boolean,
    val requiresNetwork: Boolean,
    val category: TranslationProviderCategory,
    val description: String,
    val tracksUsage: Boolean,
    val secondaryLabel: String? = null,
    val secondaryPlaceholder: String? = null,
    val secondaryIsUrl: Boolean = false,
    val helpUrl: String? = null,
) {
    MLKIT(
        displayName = "ML Kit (offline)",
        requiresApiKey = false,
        requiresNetwork = false,
        category = TranslationProviderCategory.FREE,
        description = "On-device translation. Works offline once language packs download.",
        tracksUsage = false,
    ),
    QUICK_TRANSLATE(
        displayName = "Quick Translate (free, online)",
        requiresApiKey = false,
        requiresNetwork = true,
        category = TranslationProviderCategory.FREE,
        description = "Fast online translation. No setup; needs internet. May be unstable.",
        tracksUsage = false,
    ),
    LIBRE_TRANSLATE(
        displayName = "LibreTranslate",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "Open-source engine (Argos). Get a free-tier key at portal.libretranslate.com, or point to a self-hosted instance.",
        tracksUsage = true,
        secondaryLabel = "Server URL",
        secondaryPlaceholder = "https://…",
        secondaryIsUrl = true,
        helpUrl = null,
    ),
    GOOGLE_CLOUD(
        displayName = "Google Cloud Translation",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "Official API. 500K characters/month free, paid after.",
        tracksUsage = true,
        helpUrl = "console.cloud.google.com → Translation API → API key",
    ),
    DEEPL(
        displayName = "DeepL",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "DeepL Free API. 500K characters/month free with own key.",
        tracksUsage = true,
        helpUrl = "deepl.com/pro-api (free plan)",
    ),
    AZURE(
        displayName = "Azure Translator",
        requiresApiKey = true,
        requiresNetwork = true,
        category = TranslationProviderCategory.ADVANCED,
        description = "Microsoft Azure Translator. 2M characters/month free (F0 tier).",
        tracksUsage = true,
        secondaryLabel = "Region",
        secondaryPlaceholder = "global",
        secondaryIsUrl = false,
        helpUrl = "portal.azure.com → Translator resource → Keys and Endpoint",
    );

    companion object {
        fun fromName(name: String?): TranslationProvider =
            entries.find { it.name == name } ?: MLKIT
    }
}
