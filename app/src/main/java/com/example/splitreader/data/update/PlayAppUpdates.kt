package com.example.splitreader.data.update

import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.splitreader.domain.model.UpdateState
import com.example.splitreader.domain.repository.AppUpdates
import com.example.splitreader.domain.repository.AppUpdates.Companion.SNOOZE_MILLIS
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * [AppUpdates] over Google Play's flexible in-app updates. Play answers only for installs from
 * Play; debug and sideloaded builds get an error, which reads as [UpdateState.None].
 *
 * The confirmation dialog needs the resumed Activity: `MainActivity` attaches its launcher in
 * onResume and detaches it in onPause (with two instances, only the resumed one can show it). Its result needs no handling: on "Update" Play reports PENDING to the
 * listener; on "No thanks" nothing changes and the offer stays.
 */
class PlayAppUpdates(
    private val manager: AppUpdateManager,
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) : AppUpdates {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The last version Play offered; a failed or cancelled download falls back to offering it. */
    private var lastOfferedVersion: Int? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var starting = false

    /**
     * Bumped by every install-state callback. A refresh whose answer was taken before the latest
     * callback drops it: otherwise a DOWNLOADED arriving mid-refresh was overwritten by the older
     * DOWNLOADING snapshot, and no later callback ever brought Restart back.
     */
    private var installEvents = 0

    private val listener = InstallStateUpdatedListener { s ->
        installEvents++
        when (s.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                _state.value = UpdateState.Downloading(percent(s.bytesDownloaded(), s.totalBytesToDownload()))
            InstallStatus.DOWNLOADED -> _state.value = UpdateState.Downloaded
            InstallStatus.FAILED, InstallStatus.CANCELED ->
                _state.value = lastOfferedVersion?.let { UpdateState.Available(it, isSnoozed(it)) } ?: UpdateState.None
            else -> Unit
        }
    }

    init {
        manager.registerListener(listener)
    }

    fun attach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
    }

    fun detach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (this.launcher === launcher) this.launcher = null
    }

    override suspend fun refresh() {
        val seen = installEvents
        val info = fetch() ?: return
        if (installEvents != seen) return
        _state.value = when (info.installStatus()) {
            InstallStatus.DOWNLOADED -> UpdateState.Downloaded
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> {
                // A download that outlived the process: if it fails, offer this version again.
                lastOfferedVersion = info.availableVersionCode()
                UpdateState.Downloading(percent(info.bytesDownloaded(), info.totalBytesToDownload()))
            }
            InstallStatus.INSTALLING -> _state.value
            else -> offerOf(info) ?: UpdateState.None
        }
    }

    /** False (and the button does nothing) without a resumed activity, offline, or on a second tap. */
    override suspend fun startUpdate(): Boolean {
        val launcher = launcher ?: return false
        if (starting || _state.value !is UpdateState.Available) return false
        starting = true
        try {
            // Play accepts each AppUpdateInfo for one flow only, so a retry after "No thanks" needs a fresh one.
            val info = fetch()?.takeIf { offerOf(it) != null } ?: return false
            return runCatching { manager.startUpdateFlowForResult(info, launcher, FLEXIBLE) }.getOrDefault(false)
        } finally {
            starting = false
        }
    }

    override fun completeUpdate() {
        // Play ignores this until the download is complete.
        manager.completeUpdate()
    }

    override fun snooze() {
        val offer = _state.value as? UpdateState.Available ?: return
        prefs.edit()
            .putInt(KEY_SNOOZED_VERSION, offer.versionCode)
            .putLong(KEY_SNOOZED_UNTIL, now() + SNOOZE_MILLIS)
            .apply()
        _state.value = offer.copy(snoozed = true)
    }

    private suspend fun fetch(): AppUpdateInfo? =
        try {
            manager.appUpdateInfo.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun offerOf(info: AppUpdateInfo): UpdateState.Available? {
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE || !info.isUpdateTypeAllowed(FLEXIBLE)) {
            return null
        }
        val version = info.availableVersionCode()
        lastOfferedVersion = version
        return UpdateState.Available(version, isSnoozed(version))
    }

    private fun isSnoozed(versionCode: Int): Boolean =
        prefs.getInt(KEY_SNOOZED_VERSION, -1) == versionCode && now() < prefs.getLong(KEY_SNOOZED_UNTIL, 0)

    private fun percent(bytes: Long, total: Long): Int? =
        if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else null

    companion object {
        const val PREFS = "app_updates"
        private const val KEY_SNOOZED_VERSION = "snoozed_version"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
        private val FLEXIBLE = AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
    }
}
