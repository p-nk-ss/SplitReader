package com.example.splitreader.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.splitreader.domain.model.TranslationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Keystore-backed [ApiKeyManager] that replaced the deprecated EncryptedSharedPreferences:
 * keys round-trip, survive a "process restart" (a fresh instance over the same store + Keystore),
 * and are never written to disk in plaintext.
 *
 * Runs on a device/emulator — the Android Keystore and the platform Cipher providers are required.
 */
@RunWith(AndroidJUnit4::class)
class ApiKeyManagerTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clearStore() {
        context.getSharedPreferences("translator_keys_enc", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Before fun setUp() = clearStore()
    @After fun tearDown() = clearStore()

    @Test
    fun setThenGet_roundTrips() {
        val manager = ApiKeyManager(context)
        manager.setDeepLKey("secret-deepl-123")
        assertEquals("secret-deepl-123", manager.getDeepLKey())
    }

    @Test
    fun persistedKey_survivesNewInstance() {
        ApiKeyManager(context).setAzureKey("azure-xyz")
        // A fresh instance models a process restart: it must decrypt the same on-disk blob.
        assertEquals("azure-xyz", ApiKeyManager(context).getAzureKey())
    }

    @Test
    fun storedValue_onDiskIsNotPlaintext() {
        val manager = ApiKeyManager(context)
        val secret = "plaintext-should-not-appear"
        manager.setGoogleCloudKey(secret)
        val raw = context.getSharedPreferences("translator_keys_enc", Context.MODE_PRIVATE)
            .getString("google_cloud_key", null)
        assertTrue("a value must be persisted", raw != null && raw.isNotBlank())
        assertFalse("on-disk value must be encrypted, not the raw secret", raw!!.contains(secret))
    }

    @Test
    fun blankOrNull_clearsTheKey() {
        val manager = ApiKeyManager(context)
        manager.setLibreTranslateKey("libre-key")
        assertEquals("libre-key", manager.getLibreTranslateKey())

        manager.setLibreTranslateKey("   ") // blank trims to empty -> removal
        assertNull(manager.getLibreTranslateKey())

        manager.setLibreTranslateKey("again")
        manager.setLibreTranslateKey(null)
        assertNull(manager.getLibreTranslateKey())
    }

    @Test
    fun value_isTrimmedBeforeStoring() {
        val manager = ApiKeyManager(context)
        manager.setDeepLKey("  padded-key  ")
        assertEquals("padded-key", manager.getDeepLKey())
    }

    @Test
    fun providerKeyedAccessors_dispatchToTheRightSlot() {
        val manager = ApiKeyManager(context)
        manager.setKey(TranslationProvider.DEEPL, "deepl-via-provider")
        manager.setKey(TranslationProvider.AZURE, "azure-via-provider")

        assertEquals("deepl-via-provider", manager.getKey(TranslationProvider.DEEPL))
        assertEquals("azure-via-provider", manager.getKey(TranslationProvider.AZURE))
        // A provider with no key set reads back null.
        assertNull(manager.getKey(TranslationProvider.GOOGLE_CLOUD))
    }
}
