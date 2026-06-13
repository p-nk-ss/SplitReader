package com.example.splitreader.data.repository

import android.content.Context
import com.example.splitreader.domain.repository.EntitlementRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-flag premium store (Phase 1). Persists across process restarts via SharedPreferences but is
 * wiped on uninstall — which is fine: the free limit is on the *current* library size, so a reinstall
 * resets books and entitlement together (no exploit). Phase 2 swaps this for a Play Billing-backed impl.
 */
@Singleton
class EntitlementRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : EntitlementRepository {

    private val prefs = context.getSharedPreferences("entitlement", Context.MODE_PRIVATE)
    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    override fun setPremium(premium: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM, premium).apply()
        _isPremium.value = premium
    }

    private companion object {
        const val KEY_PREMIUM = "is_premium"
    }
}
