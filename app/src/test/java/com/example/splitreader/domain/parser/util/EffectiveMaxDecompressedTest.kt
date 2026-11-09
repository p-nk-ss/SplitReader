package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MAX_DECOMPRESSED] (300 MB) is a DoS backstop sized for a generous heap; a constrained device's
 * real heap is often well under that, so the effective per-book budget must scale down with it
 * (see the doc comment on [effectiveMaxDecompressed] for why a fixed 300 MB guard doesn't actually
 * prevent an OOM on such a device).
 */
class EffectiveMaxDecompressedTest {

    @Test fun smallHeap_budgetIsAFractionOfIt() {
        // A ~192 MB-heap tablet (the device the real crash was found on).
        val maxMemory = 192L * 1024 * 1024
        val budget = effectiveMaxDecompressed(maxMemory)
        assertEquals(maxMemory / 4, budget)
    }

    @Test fun largeHeap_budgetCapsAtMaxDecompressed() {
        val maxMemory = 4L * 1024 * 1024 * 1024 // 4 GB
        assertEquals(MAX_DECOMPRESSED, effectiveMaxDecompressed(maxMemory))
    }

    @Test fun budgetNeverExceedsMaxDecompressed() {
        val budget = effectiveMaxDecompressed(Long.MAX_VALUE / 2)
        assertEquals(MAX_DECOMPRESSED, budget)
    }
}
