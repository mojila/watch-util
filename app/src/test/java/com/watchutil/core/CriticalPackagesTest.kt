package com.watchutil.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalPackagesTest {

    @Test
    fun `protected exact packages are blocked`() {
        assertTrue(CriticalPackages.isProtected("com.android.systemui"))
        assertTrue(CriticalPackages.isProtected("com.android.bluetooth"))
        assertTrue(CriticalPackages.isProtected("com.google.android.wearable.app"))
        assertTrue(CriticalPackages.isProtected("com.android.launcher3"))
    }

    @Test
    fun `protected prefixes are blocked`() {
        assertTrue(CriticalPackages.isProtected("com.android.bluetooth.audio"))
        assertTrue(CriticalPackages.isProtected("com.android.systemui.plugin"))
    }

    @Test
    fun `the app itself is protected`() {
        assertTrue(CriticalPackages.isProtected("com.watchutil"))
    }

    @Test
    fun `an ordinary package is not protected`() {
        assertFalse(CriticalPackages.isProtected("com.example.thing"))
        assertFalse(CriticalPackages.isProtected("com.android.calculator"))
    }
}
