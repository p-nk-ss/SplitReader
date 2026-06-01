package com.example.splitreader.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stores translation-provider API keys at rest using [EncryptedSharedPreferences]. */
@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Encrypted on-disk store; null if the platform Keystore/crypto is unavailable. */
    private val prefs: SharedPreferences? by lazy { buildEncryptedPrefs() }

    /**
     * Session-only fallback used when [EncryptedSharedPreferences] cannot be created. Keys are kept
     * in memory and never written to disk in plaintext — they simply do not survive a process
     * restart in that degraded state, which is the safe trade-off.
     */
    private val inMemory = mutableMapOf<String, String>()

    private fun buildEncryptedPrefs(): SharedPreferences? =
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable; keys will be kept in memory only", t)
            null
        }

    private fun read(key: String): String? =
        (prefs?.getString(key, null) ?: inMemory[key])?.takeIf { it.isNotBlank() }

    private fun write(key: String, value: String?) {
        val trimmed = value?.trim()
        val store = prefs
        if (trimmed.isNullOrBlank()) {
            inMemory.remove(key)
            store?.edit()?.remove(key)?.apply()
        } else if (store != null) {
            store.edit().putString(key, trimmed).apply()
        } else {
            inMemory[key] = trimmed
        }
    }

    fun getGoogleCloudKey(): String? = read(KEY_GOOGLE_CLOUD)

    fun setGoogleCloudKey(value: String?) = write(KEY_GOOGLE_CLOUD, value)

    fun getDeepLKey(): String? = read(KEY_DEEPL)

    fun setDeepLKey(value: String?) = write(KEY_DEEPL, value)

    fun getLibreTranslateKey(): String? = read(KEY_LIBRE)

    fun setLibreTranslateKey(value: String?) = write(KEY_LIBRE, value)

    private companion object {
        const val TAG = "ApiKeyManager"
        const val ENCRYPTED_PREFS_NAME = "translator_keys_enc"
        const val KEY_GOOGLE_CLOUD = "google_cloud_key"
        const val KEY_DEEPL = "deepl_key"
        const val KEY_LIBRE = "libre_translate_key"
    }
}
