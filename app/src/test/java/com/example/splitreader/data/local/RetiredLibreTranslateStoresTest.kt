package com.example.splitreader.data.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.TranslationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LibreTranslate is gone (v29). What earlier versions stored for it, under the keys they used, must
 * read as "no choice" (never as some other translator) and be deletable. Real SharedPreferences
 * under Robolectric: the defects that matter are key names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RetiredLibreTranslateStoresTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before
    fun clean() {
        for (name in listOf("reading_progress", "translator_keys_enc", "translator_endpoints")) {
            prefs(name).edit().clear().commit()
        }
    }

    @Test
    fun `a stored LibreTranslate choice is no choice, reported as retired until cleared`() {
        prefs("reading_progress").edit().putString("translator_provider", "LIBRE_TRANSLATE").commit()
        val manager = ReadingProgressManager(context)

        assertNull("never read as ML Kit or any other translator", manager.getTranslatorProvider())
        assertEquals("LIBRE_TRANSLATE", manager.retiredTranslatorProvider())

        manager.clearTranslatorProvider()

        assertNull(manager.retiredTranslatorProvider())
        assertFalse(prefs("reading_progress").contains("translator_provider"))
    }

    @Test
    fun `a translator the app has is not retired`() {
        val manager = ReadingProgressManager(context)
        assertNull(manager.retiredTranslatorProvider())
        manager.setTranslatorProvider(TranslationProvider.DEEPL)
        assertNull(manager.retiredTranslatorProvider())
        assertEquals(TranslationProvider.DEEPL, manager.getTranslatorProvider())
    }

    @Test
    fun `the translator notice is kept until cleared`() {
        ReadingProgressManager(context).setTranslatorNotice("switched")
        assertEquals("switched", ReadingProgressManager(context).getTranslatorNotice())
        ReadingProgressManager(context).setTranslatorNotice(null)
        assertNull(ReadingProgressManager(context).getTranslatorNotice())
    }

    @Test
    fun `LibreTranslate's key and server are deleted, Azure's region kept`() {
        prefs("translator_keys_enc").edit().putString("libre_translate_key", "blob").putString("deepl_key", "d").commit()
        prefs("translator_endpoints").edit().putString("libre_base_url", "https://libre.example.org")
            .putString("azure_region", "westeurope").commit()

        ApiKeyManager(context).forgetLibreTranslate()
        TranslatorEndpoints(context).forgetLibreTranslate()

        assertFalse(prefs("translator_keys_enc").contains("libre_translate_key"))
        assertEquals("d", prefs("translator_keys_enc").getString("deepl_key", null))
        assertFalse(prefs("translator_endpoints").contains("libre_base_url"))
        assertEquals("westeurope", TranslatorEndpoints(context).getAzureRegion())
    }
}
