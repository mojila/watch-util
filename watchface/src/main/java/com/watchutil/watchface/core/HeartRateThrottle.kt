package com.watchutil.watchface.core

/**
 * Decides when the watch face may start the next on-watch heart-rate sampling
 * burst.
 *
 * Low power is the primary constraint for this face: the heart-rate sensor is
 * never streamed continuously. Instead a short burst of samples is taken at
 * most once every [intervalMillis] while the face is interactive, and the
 * listener is unregistered again as soon as a plausible reading arrives. This
 * class holds the pure timing decision so it can be unit-tested on the JVM.
 *
 * @param intervalMillis minimum time between two sampling bursts.
 */
class HeartRateThrottle(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) {
    private var lastSampleMillis: Long? = null

    /**
     * Returns true when a new sampling burst is allowed.
     *
     * The very first call after construction (or after [reset]) always returns
     * true, so the face paints a live value as soon as it becomes interactive.
     * The sensor is never sampled in ambient or while the face is not visible,
     * hence the [isInteractive] guard.
     *
     * @param nowMillis current wall-clock time in milliseconds.
     * @param isInteractive true only when the face is visible and interactive.
     */
    fun shouldSample(nowMillis: Long, isInteractive: Boolean): Boolean {
        if (!isInteractive) return false
        val last = lastSampleMillis
        if (last == null || nowMillis - last >= intervalMillis) {
            lastSampleMillis = nowMillis
            return true
        }
        return false
    }

    /** Forgets the last sample time so the next interactive call samples again. */
    fun reset() {
        lastSampleMillis = null
    }

    companion object {
        /** 45 s: frequent enough to feel live, sparse enough to spare battery. */
        const val DEFAULT_INTERVAL_MILLIS = 45_000L
    }
}

/**
 * A heart-rate reading together with the time it was taken.
 *
 * A reading older than its maximum age is treated as stale and the watch face
 * falls back to the complication slot.
 */
data class HeartRateReading(
    val valueBpm: Int?,
    val timestampMillis: Long,
) {
    /**
     * Whether this reading is recent enough to display.
     *
     * A null [valueBpm] is never fresh: there is nothing to show.
     */
    fun isFresh(nowMillis: Long, maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): Boolean {
        if (valueBpm == null) return false
        val age = nowMillis - timestampMillis
        return age in 0..maxAgeMillis
    }

    companion object {
        /** 2 min: long enough to survive a throttle gap, short enough to be honest. */
        const val DEFAULT_MAX_AGE_MILLIS = 120_000L
    }
}
