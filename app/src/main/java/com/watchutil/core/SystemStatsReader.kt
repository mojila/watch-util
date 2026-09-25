package com.watchutil.core

import java.io.File

/** A single instantaneous reading of device resource usage. */
data class SystemStats(
    val cpuPercent: Float,
    val totalRamBytes: Long,
    val freeRamBytes: Long,
    val usedRamBytes: Long,
    /** True when the CPU figure came from the privileged bridge, not directly. */
    val cpuFromBridge: Boolean = false,
) {
    val usedRamPercent: Float
        get() = if (totalRamBytes <= 0) 0f else usedRamBytes.toFloat() / totalRamBytes * 100f

    companion object {
        val EMPTY = SystemStats(0f, 0L, 0L, 0L)
    }
}

/**
 * Reads CPU and RAM usage.
 *
 * RAM always comes from `/proc/meminfo`, which is readable by an ordinary app.
 *
 * CPU comes from `/proc/stat`, but SELinux denies that file to normal app UIDs
 * on recent Android versions (confirmed on Android 14 / Wear OS 5). When the
 * direct read is denied, the caller can supply the raw `cpu` line it obtained
 * through the privileged bridge; the delta baseline is kept here either way.
 */
class SystemStatsReader {
    private var lastTotal = 0L
    private var lastIdle = 0L
    private var hasBaseline = false
    private var directReadableCache: Boolean? = null

    /**
     * @param externalCpuLine the first line of `/proc/stat` fetched via the
     *   bridge, or null to read it directly.
     */
    fun read(externalCpuLine: String? = null): SystemStats {
        val cpuLine = externalCpuLine ?: readProcStatLine()
        val fromBridge = externalCpuLine != null
        val cpu = cpuLine?.let { cpuPercentFromLine(it) } ?: 0f
        val (total, free) = readRam()
        val used = (total - free).coerceAtLeast(0L)
        return SystemStats(
            cpuPercent = cpu,
            totalRamBytes = total,
            freeRamBytes = free,
            usedRamBytes = used,
            cpuFromBridge = fromBridge && cpuLine != null,
        )
    }

    /** Primes the CPU delta baseline so the first real reading is meaningful. */
    fun prime(externalCpuLine: String? = null) {
        hasBaseline = false
        read(externalCpuLine)
    }

    /**
     * Whether `/proc/stat` can be read by this app. Cached after the first
     * probe because the answer does not change while the process lives.
     */
    fun isDirectCpuReadable(): Boolean {
        directReadableCache?.let { return it }
        val readable = readProcStatLine() != null
        directReadableCache = readable
        return readable
    }

    /** Returns the first line of /proc/stat, or null when access is denied. */
    private fun readProcStatLine(): String? = try {
        File("/proc/stat").bufferedReader().use { it.readLine() }
    } catch (_: Exception) {
        null
    }

    private fun cpuPercentFromLine(line: String): Float {
        // Format: cpu user nice system idle iowait irq softirq steal ...
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "cpu") return 0f

        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return 0f

        val idle = values[3] + (values.getOrNull(4) ?: 0L) // idle + iowait
        val total = values.sum()

        if (!hasBaseline) {
            lastTotal = total
            lastIdle = idle
            hasBaseline = true
            return 0f
        }

        val totalDelta = total - lastTotal
        val idleDelta = idle - lastIdle
        lastTotal = total
        lastIdle = idle

        if (totalDelta <= 0L) return 0f
        val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
        return (busy.toFloat() / totalDelta.toFloat() * 100f).coerceIn(0f, 100f)
    }

    private fun readRam(): Pair<Long, Long> {
        var total = 0L
        var available = 0L
        try {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") -> total = parseKb(line)
                    line.startsWith("MemAvailable:") -> available = parseKb(line)
                }
            }
        } catch (_: Exception) {
            return 0L to 0L
        }
        // Fall back to MemFree when MemAvailable is missing (very old kernels).
        if (available == 0L) {
            try {
                File("/proc/meminfo").forEachLine { line ->
                    if (line.startsWith("MemFree:")) available = parseKb(line)
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        return total to available
    }

    private fun parseKb(line: String): Long {
        val kb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
        return kb * 1024L
    }
}
