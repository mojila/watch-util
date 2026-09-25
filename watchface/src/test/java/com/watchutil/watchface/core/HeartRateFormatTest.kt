package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateFormatTest {

    @Test
    fun `null bpm formats as placeholder`() {
        assertEquals("--", HeartRateFormat.formatBpm(null))
    }

    @Test
    fun `zero or negative bpm formats as placeholder`() {
        assertEquals("--", HeartRateFormat.formatBpm(0))
        assertEquals("--", HeartRateFormat.formatBpm(-5))
    }

    @Test
    fun `normal bpm formats as its digits`() {
        assertEquals("72", HeartRateFormat.formatBpm(72))
        assertEquals("150", HeartRateFormat.formatBpm(150))
    }

    @Test
    fun `fresh live reading beats the complication fallback`() {
        val reading = HeartRateReading(valueBpm = 68, timestampMillis = 10_000L)
        val text = HeartRateFormat.displayText(
            liveReading = reading,
            complicationText = "80",
            nowMillis = 10_000L,
        )
        assertEquals("68", text)
    }

    @Test
    fun `stale live reading falls back to the complication`() {
        val reading = HeartRateReading(valueBpm = 68, timestampMillis = 0L)
        val text = HeartRateFormat.displayText(
            liveReading = reading,
            complicationText = "80",
            nowMillis = 200_000L,
            maxAgeMillis = 120_000L,
        )
        assertEquals("80", text)
    }

    @Test
    fun `no live and no complication shows placeholder`() {
        assertEquals(
            "--",
            HeartRateFormat.displayText(
                liveReading = null,
                complicationText = null,
                nowMillis = 0L,
            ),
        )
    }

    @Test
    fun `blank complication text is treated as absent`() {
        assertEquals(
            "--",
            HeartRateFormat.displayText(
                liveReading = null,
                complicationText = "   ",
                nowMillis = 0L,
            ),
        )
    }

    @Test
    fun `complication text is trimmed`() {
        assertEquals(
            "75",
            HeartRateFormat.displayText(
                liveReading = null,
                complicationText = " 75 ",
                nowMillis = 0L,
            ),
        )
    }

    @Test
    fun `null bpm live reading falls back to the complication`() {
        val reading = HeartRateReading(valueBpm = null, timestampMillis = 10_000L)
        assertEquals(
            "88",
            HeartRateFormat.displayText(
                liveReading = reading,
                complicationText = "88",
                nowMillis = 10_000L,
            ),
        )
    }

    @Test
    fun `isLive is true only for a fresh non null reading`() {
        val fresh = HeartRateReading(72, 10_000L)
        assertTrue(HeartRateFormat.isLive(fresh, nowMillis = 10_000L))
        assertFalse(HeartRateFormat.isLive(fresh, nowMillis = 500_000L))
        assertFalse(HeartRateFormat.isLive(null, nowMillis = 10_000L))
        assertFalse(HeartRateFormat.isLive(HeartRateReading(null, 10_000L), nowMillis = 10_000L))
    }
}
