package com.watchutil.watchface.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateThrottleTest {

    @Test
    fun `first interactive sample is allowed`() {
        val throttle = HeartRateThrottle(intervalMillis = 45_000L)
        assertTrue(throttle.shouldSample(nowMillis = 1_000L, isInteractive = true))
    }

    @Test
    fun `second sample is blocked before the interval elapses`() {
        val throttle = HeartRateThrottle(intervalMillis = 45_000L)
        assertTrue(throttle.shouldSample(10_000L, isInteractive = true))
        assertFalse(throttle.shouldSample(10_000L + 44_999L, isInteractive = true))
    }

    @Test
    fun `sample is allowed once the interval elapses`() {
        val throttle = HeartRateThrottle(intervalMillis = 45_000L)
        assertTrue(throttle.shouldSample(10_000L, isInteractive = true))
        assertFalse(throttle.shouldSample(54_999L, isInteractive = true))
        assertTrue(throttle.shouldSample(55_000L, isInteractive = true))
    }

    @Test
    fun `never allowed when not interactive`() {
        val throttle = HeartRateThrottle(intervalMillis = 45_000L)
        assertFalse(throttle.shouldSample(1_000L, isInteractive = false))
        assertFalse(throttle.shouldSample(1_000_000L, isInteractive = false))
    }

    @Test
    fun `non interactive calls do not consume the interval`() {
        val throttle = HeartRateThrottle(intervalMillis = 45_000L)
        assertFalse(throttle.shouldSample(1_000L, isInteractive = false))
        // First interactive call must still be immediate.
        assertTrue(throttle.shouldSample(2_000L, isInteractive = true))
    }

    @Test
    fun `reset allows sampling again immediately`() {
        val throttle = HeartRateThrottle(intervalMillis = 45_000L)
        assertTrue(throttle.shouldSample(10_000L, isInteractive = true))
        assertFalse(throttle.shouldSample(11_000L, isInteractive = true))
        throttle.reset()
        assertTrue(throttle.shouldSample(11_000L, isInteractive = true))
    }

    @Test
    fun `fresh reading within max age is fresh`() {
        val reading = HeartRateReading(valueBpm = 72, timestampMillis = 100_000L)
        assertTrue(reading.isFresh(nowMillis = 100_000L, maxAgeMillis = 120_000L))
        assertTrue(reading.isFresh(nowMillis = 219_999L, maxAgeMillis = 120_000L))
    }

    @Test
    fun `fresh reading exactly at max age is still fresh`() {
        val reading = HeartRateReading(valueBpm = 72, timestampMillis = 100_000L)
        assertTrue(reading.isFresh(nowMillis = 220_000L, maxAgeMillis = 120_000L))
    }

    @Test
    fun `reading older than max age is stale`() {
        val reading = HeartRateReading(valueBpm = 72, timestampMillis = 100_000L)
        assertFalse(reading.isFresh(nowMillis = 220_001L, maxAgeMillis = 120_000L))
    }

    @Test
    fun `null bpm is never fresh`() {
        val reading = HeartRateReading(valueBpm = null, timestampMillis = 100_000L)
        assertFalse(reading.isFresh(nowMillis = 100_000L, maxAgeMillis = 120_000L))
    }

    @Test
    fun `default interval is 45 seconds`() {
        assertEquals(45_000L, HeartRateThrottle.DEFAULT_INTERVAL_MILLIS)
    }

    @Test
    fun `default max age is 120 seconds`() {
        assertEquals(120_000L, HeartRateReading.DEFAULT_MAX_AGE_MILLIS)
    }
}
