package com.example.splitreader.domain.model

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationLockTest {

    @Test
    fun `AUTO maps to unspecified so system auto-rotate is respected`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, OrientationLock.AUTO.toActivityInfo())
    }

    @Test
    fun `LANDSCAPE maps to sensor landscape`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            OrientationLock.LANDSCAPE.toActivityInfo(),
        )
    }

    @Test
    fun `PORTRAIT maps to sensor portrait`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            OrientationLock.PORTRAIT.toActivityInfo(),
        )
    }

    @Test
    fun `tablet defaults to landscape`() {
        assertEquals(OrientationLock.LANDSCAPE, defaultOrientationLock(isTablet = true))
    }

    @Test
    fun `phone defaults to portrait`() {
        assertEquals(OrientationLock.PORTRAIT, defaultOrientationLock(isTablet = false))
    }
}
