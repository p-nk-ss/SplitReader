package com.example.splitreader.data.local

/** Result of validating a user-entered translator base URL. */
sealed interface UrlResult {
    data class Valid(val url: String) : UrlResult
    data class Invalid(val reason: String) : UrlResult
}

/**
 * Normalizes a LibreTranslate base URL, rejecting cleartext `http://`. Modern Android
 * (targetSdk 36) blocks cleartext by default and the app ships no network-security-config, so an
 * `http://` endpoint would silently fail to connect — we reject it up front with a clear reason.
 * A bare host (no scheme) is upgraded to `https://`; an explicit `https://` is kept as-is.
 */
fun normalizeLibreUrl(raw: String): UrlResult {
    val trimmed = raw.trim().trimEnd('/')
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ->
            UrlResult.Invalid("Cleartext http:// is blocked on modern Android — use https://")
        trimmed.startsWith("https://", ignoreCase = true) -> UrlResult.Valid(trimmed)
        else -> UrlResult.Valid("https://$trimmed")
    }
}
