package com.example.splitreader.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences =
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
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", t)
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }

    fun getGoogleCloudKey(): String? = prefs.getString(KEY_GOOGLE_CLOUD, null)?.takeIf { it.isNotBlank() }

    fun setGoogleCloudKey(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GOOGLE_CLOUD) else putString(KEY_GOOGLE_CLOUD, value.trim())
        }.apply()
    }

    fun getDeepLKey(): String? = prefs.getString(KEY_DEEPL, null)?.takeIf { it.isNotBlank() }

    fun setDeepLKey(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_DEEPL) else putString(KEY_DEEPL, value.trim())
        }.apply()
    }

    fun getLibreTranslateKey(): String? = prefs.getString(KEY_LIBRE, null)?.takeIf { it.isNotBlank() }

    fun setLibreTranslateKey(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LIBRE) else putString(KEY_LIBRE, value.trim())
        }.apply()
    }

    private companion object {
        const val TAG = "ApiKeyManager"
        const val ENCRYPTED_PREFS_NAME = "translator_keys_enc"
        const val FALLBACK_PREFS_NAME = "translator_keys"
        const val KEY_GOOGLE_CLOUD = "google_cloud_key"
        const val KEY_DEEPL = "deepl_key"
        const val KEY_LIBRE = "libre_translate_key"
    }
}
