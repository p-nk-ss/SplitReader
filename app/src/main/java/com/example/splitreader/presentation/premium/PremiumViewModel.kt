package com.example.splitreader.presentation.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.example.splitreader.data.billing.BillingManager
import com.example.splitreader.data.billing.PurchaseEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin presentation wrapper over [BillingManager] for the upgrade/restore surfaces (library-limit
 * dialog, Settings). Holds no state of its own — premium status and price live in the singleton
 * BillingManager so every screen sees the same source of truth.
 */
@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billingManager: BillingManager,
) : ViewModel() {
    val isPremium: StateFlow<Boolean> = billingManager.premium
    val priceText: StateFlow<String?> = billingManager.formattedPrice
    val events: SharedFlow<PurchaseEvent> = billingManager.events

    fun upgrade(activity: Activity) = billingManager.launchPurchase(activity)
    fun restore() = billingManager.restore()
}
