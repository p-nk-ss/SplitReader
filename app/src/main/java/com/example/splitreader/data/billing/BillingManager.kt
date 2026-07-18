package com.example.splitreader.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.splitreader.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot outcomes of a purchase or restore, surfaced to the UI for a toast/snackbar. */
sealed interface PurchaseEvent {
    data object Success : PurchaseEvent
    data object Pending : PurchaseEvent
    data object NothingToRestore : PurchaseEvent
    data object StoreNotReady : PurchaseEvent
    data class Failed(val message: String) : PurchaseEvent
}

/**
 * Wraps Google Play Billing for the single one-time "unlimited library" product. Owns the
 * [BillingClient] lifecycle, exposes [premium] (the source of truth for the entitlement) and
 * [formattedPrice] (for the upgrade button), and drives purchase + restore.
 *
 * [premium] is cached in SharedPreferences so it survives offline launches; on every connect we
 * re-query Play's (locally cached) purchases, so a refund or a purchase made on another device is
 * reflected. Purchases are acknowledged — Play auto-refunds unacknowledged ones after 3 days.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _premium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    /** True once the user owns the acknowledged premium product. */
    val premium: StateFlow<Boolean> = _premium.asStateFlow()

    private val _formattedPrice = MutableStateFlow<String?>(null)
    /** Localized price string (e.g. "$4.99") once product details load, else null. */
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _events = MutableSharedFlow<PurchaseEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PurchaseEvent> = _events.asSharedFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        connect()
    }

    private fun connect() {
        val state = billingClient.connectionState
        if (state == BillingClient.ConnectionState.CONNECTED ||
            state == BillingClient.ConnectionState.CONNECTING
        ) return
        runCatching { billingClient.startConnection(this) }
            .onFailure { Log.w(TAG, "startConnection failed", it) }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProductDetails()
            syncPurchases(emitEvents = false)
        } else {
            Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        // Reconnect lazily on the next user action; nothing to do eagerly.
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = details.firstOrNull()
                _formattedPrice.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            } else {
                Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
            }
        }
    }

    /** Launches the Play purchase sheet. No-op with a [PurchaseEvent.StoreNotReady] if details aren't loaded. */
    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            connect()
            queryProductDetails()
            _events.tryEmit(PurchaseEvent.StoreNotReady)
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    /** User-initiated "Restore purchases": re-queries Play and reports the outcome. */
    fun restore() = syncPurchases(emitEvents = true)

    /**
     * Re-queries owned purchases. Sets [premium] from the result and acknowledges any pending ack.
     * When [emitEvents] (user-initiated restore), reports Success / NothingToRestore / Failed.
     */
    private fun syncPurchases(emitEvents: Boolean) {
        connect()
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                if (emitEvents) _events.tryEmit(PurchaseEvent.Failed(result.debugMessage.ifBlank { FALLBACK_ERR }))
                return@queryPurchasesAsync
            }
            val ownedPremium = purchases.filter {
                it.isPremium() && it.purchaseState == Purchase.PurchaseState.PURCHASED && it.signatureOk()
            }
            ownedPremium.forEach { acknowledgeIfNeeded(it) }
            setPremium(ownedPremium.isNotEmpty())
            if (emitEvents) {
                _events.tryEmit(
                    if (ownedPremium.isNotEmpty()) PurchaseEvent.Success else PurchaseEvent.NothingToRestore
                )
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handleNewPurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Unit // Silent: user backed out, no error toast.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                syncPurchases(emitEvents = true)
            else ->
                _events.tryEmit(PurchaseEvent.Failed(result.debugMessage.ifBlank { FALLBACK_ERR }))
        }
    }

    private fun handleNewPurchase(purchase: Purchase) {
        if (!purchase.isPremium()) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.signatureOk()) {
                    Log.w(TAG, "Purchase signature verification failed; not granting premium")
                    _events.tryEmit(PurchaseEvent.Failed(FALLBACK_ERR))
                    return
                }
                acknowledgeIfNeeded(purchase)
                setPremium(true)
                _events.tryEmit(PurchaseEvent.Success)
            }
            Purchase.PurchaseState.PENDING -> _events.tryEmit(PurchaseEvent.Pending)
            else -> Unit
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    private fun Purchase.isPremium() = products.contains(PREMIUM_PRODUCT_ID)

    /**
     * True if the purchase's signature verifies against BILLING_PUBLIC_KEY. Fail-open: when the key
     * is unset (dev builds), verification is skipped so the purchase flow still works. TODO(P15):
     * set BILLING_PUBLIC_KEY before release so forged purchases are rejected.
     */
    private fun Purchase.signatureOk(): Boolean {
        val key = BuildConfig.BILLING_PUBLIC_KEY
        if (key.isBlank()) {
            Log.w(TAG, "BILLING_PUBLIC_KEY unset — skipping signature check (TODO before release)")
            return true
        }
        return PurchaseVerifier.verify(key, originalJson, signature)
    }

    private fun setPremium(value: Boolean) {
        if (_premium.value == value && prefs.getBoolean(KEY_PREMIUM, false) == value) return
        prefs.edit().putBoolean(KEY_PREMIUM, value).apply()
        _premium.value = value
    }

    private companion object {
        const val TAG = "BillingManager"
        const val PREFS = "billing"
        const val KEY_PREMIUM = "premium_owned"
        /** Must match the in-app product id created in Play Console. */
        const val PREMIUM_PRODUCT_ID = "premium_unlimited"
        const val FALLBACK_ERR = "Purchase failed. Please try again."
    }
}
