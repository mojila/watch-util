package com.watchutil.core

import java.util.Locale

/** Total and free capacity of a volume, in bytes. */
data class DiskSpace(
    val totalBytes: Long,
    val freeBytes: Long,
)

/**
 * Pure helpers for the "Clear caches" action.
 *
 * The action runs `pm trim-caches DESIRED_FREE_SPACE`, which asks the platform
 * to delete app cache files until the requested free space is reached. It only
 * touches caches: package data, logins and settings are untouched. Asking for
 * more free space than the volume can hold makes every cache eligible, which is
 * how "clear all caches" is spelled with a command that is not actually
 * destructive.
 *
 * Kept free of Android imports so the QA agent can unit-test it on the JVM.
 */
object CacheCleaner {

    /**
     * Parses `df -k /data`.
     *
     * The output has a header line followed by one data line per filesystem,
     * e.g.
     * ```
     * Filesystem 1K-blocks     Used Available Use% Mounted on
     * /dev/block/sda1 11000000 3000000 8000000 28% /data
     * ```
     * The `1K-blocks` column is the total and `Available` is the free space;
     * both are in 1 KiB units, so they are multiplied by 1024. Blank lines are
     * skipped and a non-numeric first data line (a warning, for instance) makes
     * the parse fail rather than producing nonsense.
     *
     * Only the first non-header line is considered, and it is validated as a
     * data row before its numbers are trusted: a `df` diagnostic such as
     * `df: 1 2 3 4 5 /data` has numeric-looking columns and would otherwise be
     * read as a real volume.
     *
     * @return the parsed [DiskSpace], or null when no valid data line is found.
     */
    fun parseDf(dfOutput: String): DiskSpace? {
        val dataLine = dfOutput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .drop(1) // header: Filesystem 1K-blocks Used Available Use% Mounted on
            .firstOrNull()
            ?: return null

        val columns = dataLine.split(Regex("\\s+"))
        // Need at least: filesystem, blocks, used, available.
        if (columns.size < 4) return null
        // The filesystem column of a real df row is always a device path; a
        // diagnostic ("df:", "ls:", ...) is not. Do not assume a particular
        // device prefix or mount point: both vary by device.
        if (!columns[0].startsWith("/")) return null
        val total = columns.getOrNull(1)?.toLongOrNull() ?: return null
        val used = columns.getOrNull(2)?.toLongOrNull() ?: return null
        val free = columns.getOrNull(3)?.toLongOrNull() ?: return null
        if (total < 0 || used < 0 || free < 0) return null
        // The use-percent column is only present when df prints it, so it is
        // optional; when present it must actually be a percentage.
        columns.getOrNull(4)?.let { if (!it.endsWith("%")) return null }
        // 1 KiB units -> bytes. Reject values that would overflow instead of
        // silently wrapping negative.
        val totalBytes = total.kibToBytesOrNull() ?: return null
        val freeBytes = free.kibToBytesOrNull() ?: return null
        // A volume reports positive capacity and non-negative free space.
        if (totalBytes <= 0L || freeBytes < 0L) return null
        return DiskSpace(totalBytes = totalBytes, freeBytes = freeBytes)
    }

    /** Converts a 1 KiB block count to bytes, or null on overflow. */
    private fun Long.kibToBytesOrNull(): Long? =
        try {
            Math.multiplyExact(this, 1024L)
        } catch (_: ArithmeticException) {
            null
        }

    /**
     * The argv that trims every cache on the volume.
     *
     * `pm trim-caches` only has to free *up to* the given target, so requesting
     * strictly more than the volume holds guarantees every cache is eligible
     * while still leaving user data alone.
     *
     * A malformed [space] (non-positive total, or a total too large to add one
     * to) falls back to the fixed large target rather than sending `pm` a
     * nonsensical target.
     */
    fun trimAllArgs(space: DiskSpace): List<String> {
        val target = if (space.totalBytes > 0L && space.totalBytes < Long.MAX_VALUE) {
            space.totalBytes + 1L
        } else {
            null
        }
        return if (target != null) {
            listOf("pm", "trim-caches", target.toString())
        } else {
            trimAllArgs()
        }
    }

    /**
     * Fallback argv used when `df` output could not be parsed: a target (1 TiB)
     * larger than any watch volume, which trims all caches.
     */
    fun trimAllArgs(): List<String> =
        listOf("pm", "trim-caches", (1L shl 40).toString())

    /**
     * Human-readable byte count, matching the dashboard's RAM formatting.
     * Lives here (rather than the UI) so the view model can build messages
     * without depending on Compose.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
