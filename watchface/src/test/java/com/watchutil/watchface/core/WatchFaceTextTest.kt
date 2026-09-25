package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchFaceTextTest {

    // --------------------------------------------------------------- 24-hour

    @Test
    fun `24h time zero pads both fields`() {
        assertEquals("09:05", WatchFaceText.time(hour = 9, minute = 5, is24Hour = true))
    }

    @Test
    fun `24h midnight is 00 00`() {
        assertEquals("00:00", WatchFaceText.time(hour = 0, minute = 0, is24Hour = true))
    }

    @Test
    fun `24h midnight adjacent is 00 01`() {
        assertEquals("00:01", WatchFaceText.time(hour = 0, minute = 1, is24Hour = true))
    }

    @Test
    fun `24h last minute of the day is 23 59`() {
        assertEquals("23:59", WatchFaceText.time(hour = 23, minute = 59, is24Hour = true))
    }

    @Test
    fun `24h noon is 12 00`() {
        assertEquals("12:00", WatchFaceText.time(hour = 12, minute = 0, is24Hour = true))
    }

    @Test
    fun `24h with seconds zero pads seconds`() {
        assertEquals(
            "08:03:07",
            WatchFaceText.timeWithSeconds(hour = 8, minute = 3, second = 7, is24Hour = true),
        )
    }

    @Test
    fun `24h midnight with seconds is 00 00 00`() {
        assertEquals(
            "00:00:00",
            WatchFaceText.timeWithSeconds(hour = 0, minute = 0, second = 0, is24Hour = true),
        )
    }

    // --------------------------------------------------------------- 12-hour

    @Test
    fun `12h midnight is 12 00`() {
        assertEquals("12:00", WatchFaceText.time(hour = 0, minute = 0, is24Hour = false))
    }

    @Test
    fun `12h midnight adjacent is 12 01`() {
        assertEquals("12:01", WatchFaceText.time(hour = 0, minute = 1, is24Hour = false))
    }

    @Test
    fun `12h afternoon wraps past noon`() {
        assertEquals("01:30", WatchFaceText.time(hour = 13, minute = 30, is24Hour = false))
    }

    @Test
    fun `12h noon stays 12`() {
        assertEquals("12:00", WatchFaceText.time(hour = 12, minute = 0, is24Hour = false))
    }

    @Test
    fun `12h late evening wraps to 11`() {
        assertEquals("11:45", WatchFaceText.time(hour = 23, minute = 45, is24Hour = false))
    }

    @Test
    fun `12h with seconds keeps zero padding`() {
        assertEquals(
            "12:00:05",
            WatchFaceText.timeWithSeconds(hour = 0, minute = 0, second = 5, is24Hour = false),
        )
    }

    @Test
    fun `out of range hours are wrapped defensively`() {
        assertEquals("01:00", WatchFaceText.time(hour = 25, minute = 0, is24Hour = true))
    }
}
