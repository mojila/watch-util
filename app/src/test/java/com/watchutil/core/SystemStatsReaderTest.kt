package com.watchutil.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * These tests read the host's /proc, so they exercise the real parsing code on
 * Linux and Android. On macOS (no /proc) they are skipped rather than failing,
 * because the same code is validated on-device.
 */
class SystemStatsReaderTest {

    private fun assumeProcAvailable() {
        assumeTrue("requires /proc", File("/proc/stat").exists())
    }

    @Test
    fun `read returns plausible values`() {
        assumeProcAvailable()
        val reader = SystemStatsReader()
        reader.prime()
        Thread.sleep(50)
        val stats = reader.read()

        assertTrue("total RAM should be positive", stats.totalRamBytes > 0)
        assertTrue("free RAM should not exceed total", stats.freeRamBytes <= stats.totalRamBytes)
        assertTrue("CPU percent within range", stats.cpuPercent in 0f..100f)
        assertTrue("used RAM percent within range", stats.usedRamPercent in 0f..100f)
    }

    @Test
    fun `first reading has no cpu delta and reports zero`() {
        assumeProcAvailable()
        val reader = SystemStatsReader()
        val first = reader.read()
        assertEquals(0f, first.cpuPercent, 0.001f)
    }

    @Test
    fun `used equals total minus free`() {
        assumeProcAvailable()
        val reader = SystemStatsReader()
        reader.prime()
        val stats = reader.read()
        assertEquals(stats.totalRamBytes - stats.freeRamBytes, stats.usedRamBytes)
    }
}
