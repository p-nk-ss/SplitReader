package com.example.splitreader.data.bergamot

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The library loads on this ABI and load() on garbage reports through lastError, not a crash. */
@RunWith(AndroidJUnit4::class)
class BergamotNativeSmokeTest {
    @Test
    fun library_loads_and_bad_config_reports_error() {
        System.loadLibrary(BergamotNative.LIBRARY)
        val handle = BergamotNative.load("not: [valid")
        assertEquals(0L, handle)
        assertTrue(BergamotNative.lastError().isNotBlank())
    }
}
