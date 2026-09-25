package com.watchutil.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCleanerTest {

    @Test
    fun `parses df output including header`() {
        val raw = """
            Filesystem      1K-blocks     Used Available Use% Mounted on
            /dev/block/sda1  11000000  3000000   8000000  28% /data
        """.trimIndent()

        val space = CacheCleaner.parseDf(raw)

        assertEquals(DiskSpace(totalBytes = 11_000_000L * 1024, freeBytes = 8_000_000L * 1024), space)
    }

    @Test
    fun `parses output with blank lines and extra whitespace`() {
        val raw = "\n  \nFilesystem 1K-blocks Used Available Use% Mounted on\n" +
            "   /dev/block/dm-7    5000000    1000000   4000000   20%  /data   \n\n"

        val space = CacheCleaner.parseDf(raw)

        assertEquals(DiskSpace(totalBytes = 5_000_000L * 1024, freeBytes = 4_000_000L * 1024), space)
    }

    @Test
    fun `returns null when there is no data line`() {
        assertNull(CacheCleaner.parseDf(""))
        assertNull(CacheCleaner.parseDf("Filesystem 1K-blocks Used Available Use% Mounted on\n"))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(CacheCleaner.parseDf("not a df table at all"))
        assertNull(CacheCleaner.parseDf("header\nfilesystem blocks used available"))
    }

    @Test
    fun `rejects a numeric-looking df diagnostic line`() {
        // Regression: `df: ...` is a diagnostic, not a filesystem row. Its
        // numbers must not be read as capacity.
        val raw = "Filesystem 1K-blocks Used Available Use% Mounted on\n" +
            "df: 1 2 3 4 5 /data\n"

        assertNull(CacheCleaner.parseDf(raw))
    }

    @Test
    fun `parses this watch's real df -k data output`() {
        // Real output from this watch: the mount point is /data/user/0 and must
        // not be special-cased.
        val raw = "Filesystem 1K-blocks Used Available Use% Mounted on\n" +
            "/dev/block/dm-44  24639340 2019932  22488336   9% /data/user/0\n"

        val space = CacheCleaner.parseDf(raw)

        assertEquals(
            DiskSpace(totalBytes = 24_639_340L * 1024, freeBytes = 22_488_336L * 1024),
            space,
        )
    }

    @Test
    fun `returns null when the block counts overflow`() {
        val header = "Filesystem 1K-blocks Used Available Use% Mounted on\n"

        assertNull(CacheCleaner.parseDf(header + "/dev/block/dm-1 9223372036854775807 0 1 0% /data\n"))
        assertNull(CacheCleaner.parseDf(header + "/dev/block/dm-1 1 0 9223372036854775807 0% /data\n"))
    }

    @Test
    fun `rejects negative block counts`() {
        val header = "Filesystem 1K-blocks Used Available Use% Mounted on\n"

        assertNull(CacheCleaner.parseDf(header + "/dev/block/dm-1 -1 0 100 0% /data\n"))
        assertNull(CacheCleaner.parseDf(header + "/dev/block/dm-1 100 0 -1 0% /data\n"))
    }

    @Test
    fun `trimAllArgs targets strictly more than the volume size`() {
        val space = DiskSpace(totalBytes = 11_000_000L * 1024, freeBytes = 8_000_000L * 1024)

        val args = CacheCleaner.trimAllArgs(space)

        assertEquals("pm", args[0])
        assertEquals("trim-caches", args[1])
        assertEquals(3, args.size)
        assertTrue(args[2].toLong() > space.totalBytes)
    }

    @Test
    fun `trimAllArgs never produces a non-positive target`() {
        // A bad DiskSpace must not leak into the argv as a <= 0 target.
        val bad = DiskSpace(totalBytes = 0L, freeBytes = 0L)
        val args = CacheCleaner.trimAllArgs(bad)

        assertEquals(listOf("pm", "trim-caches"), args.take(2))
        assertTrue(args[2].toLong() > 0L)
    }

    @Test
    fun `trimAllArgs fallback is a positive large target`() {
        val args = CacheCleaner.trimAllArgs()

        assertEquals(listOf("pm", "trim-caches"), args.take(2))
        assertEquals(3, args.size)
        assertTrue(args[2].toLong() > 0L)
    }

    @Test
    fun `formatBytes renders megabytes and gigabytes`() {
        assertEquals("0 MB", CacheCleaner.formatBytes(0L))
        assertEquals("68 MB", CacheCleaner.formatBytes(68L * 1024 * 1024))
        assertEquals("2.00 GB", CacheCleaner.formatBytes(2L * 1024 * 1024 * 1024))
    }
}
