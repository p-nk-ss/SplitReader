package com.example.splitreader.data.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCacheKeyTest {

    private fun key(text: String, p: TranslationProvider = TranslationProvider.MLKIT,
                    s: Language = Language.ENGLISH, t: Language = Language.RUSSIAN) =
        TranslationCacheKey.compute(p, text, s, t)

    @Test fun deterministic_sameInputSameKey() {
        assertEquals(key("Hello world"), key("Hello world"))
    }

    @Test fun differentText_differentKey() {
        assertNotEquals(key("Hello world"), key("Goodbye world"))
    }

    @Test fun providerChangesKey() {
        assertNotEquals(key("x", p = TranslationProvider.MLKIT), key("x", p = TranslationProvider.DEEPL))
    }

    @Test fun targetLanguageChangesKey() {
        assertNotEquals(key("x", t = Language.RUSSIAN), key("x", t = Language.GERMAN))
    }

    @Test fun sourceLanguageChangesKey() {
        assertNotEquals(key("x", s = Language.ENGLISH), key("x", s = Language.GERMAN))
    }

    @Test fun containsProviderAndLangCodes() {
        val k = TranslationCacheKey.compute(
            TranslationProvider.MLKIT, "x", Language.ENGLISH, Language.RUSSIAN,
        )
        assertTrue(k.startsWith("MLKIT_"))
        assertTrue(k.endsWith("_${Language.ENGLISH.code}_${Language.RUSSIAN.code}"))
    }

    @Test fun knownVector_sha256OfEmpty() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val k = key("")
        assertTrue(k.contains("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    }
}
