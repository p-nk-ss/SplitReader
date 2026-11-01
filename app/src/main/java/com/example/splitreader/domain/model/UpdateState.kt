package com.example.splitreader.domain.model

/** Where a newer version of the app stands, as Google Play reports it. */
sealed interface UpdateState {
    /** Up to date, or Play can't tell (not installed from Play, offline, Play unavailable). */
    data object None : UpdateState

    /** A newer version can be downloaded. [snoozed]: the reader said "Not now" to this version recently. */
    data class Available(val versionCode: Int, val snoozed: Boolean) : UpdateState

    /** Downloading in the background; [percent] is null until Play reports the size. */
    data class Downloading(val percent: Int?) : UpdateState

    /** Downloaded; installs (and restarts the app) on [com.example.splitreader.domain.repository.AppUpdates.completeUpdate]. */
    data object Downloaded : UpdateState
}
