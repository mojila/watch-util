package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySnapshotTest {

    @Test
    fun `level is formatted as a percentage`() {
        assertEquals("82%", BatterySnapshot(levelPercent = 82, isCharging = false).formatLevel())
    }

    @Test
    fun `zero and one hundred format correctly`() {
        assertEquals("0%", BatterySnapshot(0, isCharging = false).formatLevel())
        assertEquals("100%", BatterySnapshot(100, isCharging = true).formatLevel())
    }

    @Test
    fun `fromRaw clamps above one hundred`() {
        assertEquals(100, BatterySnapshot.fromRaw(150, isCharging = false).levelPercent)
    }

    @Test
    fun `fromRaw clamps below zero`() {
        assertEquals(0, BatterySnapshot.fromRaw(-20, isCharging = false).levelPercent)
    }

    @Test
    fun `fromRaw keeps an in-range level untouched`() {
        val snapshot = BatterySnapshot.fromRaw(55, isCharging = true)
        assertEquals(55, snapshot.levelPercent)
        assertTrue(snapshot.isCharging)
    }

    @Test
    fun `charging suffix is shown only while charging`() {
        assertEquals("⚡", BatterySnapshot(50, isCharging = true).formatChargingSuffix())
        assertEquals("", BatterySnapshot(50, isCharging = false).formatChargingSuffix())
    }

    @Test
    fun `unknown snapshot is empty and not charging`() {
        assertEquals(0, BatterySnapshot.UNKNOWN.levelPercent)
        assertFalse(BatterySnapshot.UNKNOWN.isCharging)
    }

    @Test
    fun `charging flag is preserved`() {
        assertTrue(BatterySnapshot(10, isCharging = true).isCharging)
        assertFalse(BatterySnapshot(10, isCharging = false).isCharging)
    }
}
