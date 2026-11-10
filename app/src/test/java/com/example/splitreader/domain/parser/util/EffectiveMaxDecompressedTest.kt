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

    // ── effectiveMaxBinaryBytes: a tighter heap fraction for one in-memory <binary> decode ──────
    // Decoding one FB2 <binary> transiently holds ~3.75x its own cap in contiguous heap at once
    // (the accumulating StringBuilder, its toString() copy, decodeBase64's whitespace-stripped
    // copy, and the decoded ByteArray) -- heap/4 (effectiveMaxDecompressed's default) would let
    // that hit ~94% of the heap; heap/16 keeps it under ~24%.

    @Test fun smallHeap_binaryBudgetIsASixteenthOfIt() {
        val maxMemory = 192L * 1024 * 1024
        val budget = effectiveMaxBinaryBytes(maxMemory)
        assertEquals(maxMemory / 16, budget)
    }

    @Test fun largeHeap_binaryBudgetCapsAtMaxDecompressed() {
        val maxMemory = 16L * 1024 * 1024 * 1024 // 16 GB
        assertEquals(MAX_DECOMPRESSED, effectiveMaxBinaryBytes(maxMemory))
    }
}
