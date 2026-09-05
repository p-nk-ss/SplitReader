package com.example.splitreader.data.bergamot

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** The library loads on this ABI and reports failures through lastError instead of crashing. */
@RunWith(AndroidJUnit4::class)
class BergamotNativeSmokeTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            System.loadLibrary(BergamotNative.LIBRARY)
        }

        /** U+1F642, a 4-byte UTF-8 codepoint — a surrogate pair in Java's UTF-16. */
        private const val ASTRAL = "🙂 test"
    }

    @Test
    fun library_loads_and_bad_config_reports_error() {
        val handle = BergamotNative.load("not: [valid")
        assertEquals(0L, handle)
        assertTrue(BergamotNative.lastError().isNotBlank())
    }

    /**
     * A string containing an astral codepoint must cross the JNI boundary without aborting. Under
     * CheckJNI a modified-UTF-8 mix-up in either direction kills the process, so simply returning
     * is the assertion that matters.
     */
    @Test
    fun astral_codepoints_cross_the_boundary_without_aborting() {
        assertNull(BergamotNative.translate(0L, ASTRAL))
        assertTrue(BergamotNative.lastError().isNotBlank())

        assertEquals(0L, BergamotNative.load("beam-size: $ASTRAL\nmodels: [nope"))
        assertTrue(BergamotNative.lastError().isNotBlank())
    }

    /** Unloading a handle that was never allocated must be a no-op, not a crash. */
    @Test
    fun unload_of_null_handle_is_harmless() {
        BergamotNative.unload(0L)
    }
}
