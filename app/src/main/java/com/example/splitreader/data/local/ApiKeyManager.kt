package com.example.splitreader.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.splitreader.domain.model.TranslationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores translation-provider API keys at rest, encrypted with a non-exportable AES-256-GCM key
 * held in the Android Keystore. Values on disk are `base64(iv || ciphertext || tag)`, so the
 * SharedPreferences file never contains plaintext.
 *
 * This replaces Jetpack Security's `EncryptedSharedPreferences`, which only ever shipped as an
 * alpha on the 1.1.0 line and is now deprecated. Using the Keystore directly removes that
 * dependency while keeping the same threat model (key material never leaves the Keystore).
 */
@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** Plain prefs holding only encrypted blobs; kept under the old name so backup rules still exclude it. */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Session-only fallback used when the Keystore cannot provide a key (some locked or broken
     * devices). Keys are kept in memory and never written to disk in plaintext — they simply do
     * not survive a process restart in that degraded state, which is the safe trade-off.
     */
    private val inMemory = mutableMapOf<String, String>()

    /** Lazily resolved AES key living in the AndroidKeyStore; null if the platform can't provide one. */
    private val secretKey: SecretKey? by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey? =
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: generateKey()
        } catch (t: Throwable) {
            Log.w(TAG, "AndroidKeyStore unavailable; keys will be kept in memory only", t)
            null
        }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()

    /** Encrypts [plaintext] to `base64(iv || ciphertext)`, or null if the Keystore is unavailable. */
    private fun encrypt(plaintext: String): String? {
        val key = secretKey ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            val combined = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "encrypt failed", t)
            null
        }
    }

    /** Reverses [encrypt]; returns null on any failure (missing key, tampered blob, stale data). */
    private fun decrypt(stored: String): String? {
        val key = secretKey ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "decrypt failed", t)
            null
        }
    }

    private fun read(key: String): String? {
        val value = prefs.getString(key, null)?.let(::decrypt) ?: inMemory[key]
        return value?.takeIf { it.isNotBlank() }
    }

    private fun write(key: String, value: String?) {
        val trimmed = value?.trim()
        if (trimmed.isNullOrBlank()) {
            inMemory.remove(key)
            prefs.edit().remove(key).apply()
            return
        }
        val encrypted = encrypt(trimmed)
        if (encrypted != null) {
            inMemory.remove(key)
            prefs.edit().putString(key, encrypted).apply()
        } else {
            // Keystore unavailable — keep in memory only, never write plaintext to disk.
            inMemory[key] = trimmed
        }
    }

    fun getGoogleCloudKey(): String? = read(KEY_GOOGLE_CLOUD)

    fun setGoogleCloudKey(value: String?) = write(KEY_GOOGLE_CLOUD, value)

    fun getDeepLKey(): String? = read(KEY_DEEPL)

    fun setDeepLKey(value: String?) = write(KEY_DEEPL, value)

    fun getLibreTranslateKey(): String? = read(KEY_LIBRE)

    fun setLibreTranslateKey(value: String?) = write(KEY_LIBRE, value)

    fun getAzureKey(): String? = read(KEY_AZURE)

    fun setAzureKey(value: String?) = write(KEY_AZURE, value)

    /** Provider-keyed accessor; dispatches to the matching per-provider getter. */
    fun getKey(provider: TranslationProvider): String? = when (provider) {
        TranslationProvider.GOOGLE_CLOUD -> getGoogleCloudKey()
        TranslationProvider.DEEPL -> getDeepLKey()
        TranslationProvider.LIBRE_TRANSLATE -> getLibreTranslateKey()
        TranslationProvider.AZURE -> getAzureKey()
        else -> null
    }

    /** Provider-keyed mutator; dispatches to the matching per-provider setter. */
    fun setKey(provider: TranslationProvider, value: String?) {
        when (provider) {
            TranslationProvider.GOOGLE_CLOUD -> setGoogleCloudKey(value)
            TranslationProvider.DEEPL -> setDeepLKey(value)
            TranslationProvider.LIBRE_TRANSLATE -> setLibreTranslateKey(value)
            TranslationProvider.AZURE -> setAzureKey(value)
            else -> Unit
        }
    }

    private companion object {
        const val TAG = "ApiKeyManager"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "translator_keys_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val ENCRYPTED_PREFS_NAME = "translator_keys_enc"
        const val KEY_GOOGLE_CLOUD = "google_cloud_key"
        const val KEY_DEEPL = "deepl_key"
        const val KEY_LIBRE = "libre_translate_key"
        const val KEY_AZURE = "azure_key"
    }
}
