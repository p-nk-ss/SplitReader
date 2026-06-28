package com.example.splitreader.data.repository

import android.content.Context
import com.example.splitreader.data.billing.BillingManager
import com.example.splitreader.domain.repository.EntitlementRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Premium = a verified Play Billing purchase ([BillingManager]) OR a debug override. The override is
 * a local SharedPreferences flag flipped only by the debug toggle in Settings; release builds rely
 * entirely on Billing. Callers see a single [isPremium] flow and don't care which source unlocked it.
 */
@Singleton
class EntitlementRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    billingManager: BillingManager,
) : EntitlementRepository {

    private val prefs = context.getSharedPreferences("entitlement", Context.MODE_PRIVATE)
    private val debugOverride = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))

    override val isPremium: Flow<Boolean> =
        combine(billingManager.premium, debugOverride) { purchased, debug -> purchased || debug }

    override fun setPremium(premium: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM, premium).apply()
        debugOverride.value = premium
    }

    private companion object {
        const val KEY_PREMIUM = "is_premium"
    }
}
