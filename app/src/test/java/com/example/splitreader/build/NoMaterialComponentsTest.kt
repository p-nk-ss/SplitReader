package com.example.splitreader.build

import org.junit.Assert.fail
import org.junit.Test

/**
 * The app is all Compose; Material Components (the View library) only supplied an XML theme parent.
 * Play flags its MaterialDatePicker for calling Window.setStatusBarColor/setNavigationBarColor,
 * which Android 15 no longer supports, and R8 kept those classes in the release. Keep it out.
 */
class NoMaterialComponentsTest {
    @Test
    fun materialComponentsIsNotADependency() {
        val name = "com.google.android.material.datepicker.MaterialDatePicker"
        try {
            Class.forName(name)
            fail("$name is on the classpath: com.google.android.material is a dependency again")
        } catch (_: ClassNotFoundException) {
        }
    }
}
