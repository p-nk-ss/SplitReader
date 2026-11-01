package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.UpdateState
import kotlinx.coroutines.flow.StateFlow

/** Port for in-app updates: a newer version is offered, downloaded in the background, then installed. */
interface AppUpdates {
    val state: StateFlow<UpdateState>

    /** Asks the store again. Never throws; a failure leaves [state] as it was. */
    suspend fun refresh()

    /** Opens the store's confirmation for the available version. False when it can't be shown. */
    suspend fun startUpdate(): Boolean

    /** Installs a downloaded update; the app restarts. */
    fun completeUpdate()

    /** "Not now": hides the offer of the current version for [SNOOZE_MILLIS]. */
    fun snooze()

    companion object {
        const val SNOOZE_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
