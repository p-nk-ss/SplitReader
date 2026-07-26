package com.example.splitreader.domain.model

import android.content.pm.ActivityInfo

/**
 * User-selectable screen-orientation policy for the single Activity. Kept in the domain layer with
 * pure mappings so both the persistence default and the Activity's requestedOrientation derive from
 * one source and stay JVM-testable (the ActivityInfo constants are compile-time ints — inlined, so
 * these functions never touch the android.jar stub at unit-test runtime).
 */
enum class OrientationLock { AUTO, LANDSCAPE, PORTRAIT }

/** Maps the policy to an [ActivityInfo] `SCREEN_ORIENTATION_*` value for `Activity.requestedOrientation`. */
fun OrientationLock.toActivityInfo(): Int = when (this) {
    OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}

/**
 * First-launch default when the user has not chosen yet: phones read best upright, tablets in
 * landscape (also preserves the pre-2a forced-landscape UX for existing tablet installs).
 */
fun defaultOrientationLock(isTablet: Boolean): OrientationLock =
    if (isTablet) OrientationLock.LANDSCAPE else OrientationLock.PORTRAIT
