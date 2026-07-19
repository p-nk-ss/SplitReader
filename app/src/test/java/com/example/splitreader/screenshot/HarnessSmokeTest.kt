package com.example.splitreader.screenshot

import androidx.compose.material3.Text
import org.junit.Test

/**
 * Proves the Roborazzi + Robolectric pipeline actually renders a Compose screen to a PNG
 * on this machine. No production screen is involved — just a trivial [Text].
 */
class HarnessSmokeTest : ScreenshotTest() {
    @Test
    fun smoke() = captureScreen("smoke") { Text("Mirrolit screenshot harness") }
}
