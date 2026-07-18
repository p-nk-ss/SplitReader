package com.example.splitreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorUrlValidatorTest {

    @Test fun httpsIsValidUnchanged() {
        assertEquals(UrlResult.Valid("https://libretranslate.com"),
            normalizeLibreUrl("https://libretranslate.com"))
    }

    @Test fun bareHostGetsHttpsPrefix() {
        assertEquals(UrlResult.Valid("https://myserver.local"),
            normalizeLibreUrl("myserver.local"))
    }

    @Test fun trailingSlashAndSpacesTrimmed() {
        assertEquals(UrlResult.Valid("https://example.com"),
            normalizeLibreUrl("  https://example.com/  "))
    }

    @Test fun httpIsRejected() {
        val r = normalizeLibreUrl("http://example.com")
        assertTrue(r is UrlResult.Invalid)
    }

    @Test fun httpUppercaseIsRejected() {
        assertTrue(normalizeLibreUrl("HTTP://example.com") is UrlResult.Invalid)
    }
}
