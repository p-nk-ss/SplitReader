package com.example.splitreader.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the user's premium ("unlimited library") entitlement.
 *
 * Phase 1: backed by a local flag (default `false`) with a debug-only override so the free-tier
 * friction and the unlocked state can both be tested. Phase 2 replaces ONLY the implementation
 * with Google Play Billing (a verified, reinstall-restorable purchase) — no caller changes.
 */
interface EntitlementRepository {
    /** Emits `true` once the user has unlocked unlimited library, `false` on the free tier. */
    val isPremium: Flow<Boolean>

    /**
     * Debug/testing override. Real entitlement in Phase 2 comes from Play Billing; callers other
     * than the debug toggle must not use this.
     */
    fun setPremium(premium: Boolean)
}
