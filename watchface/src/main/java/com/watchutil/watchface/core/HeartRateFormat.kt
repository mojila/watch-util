package com.watchutil.watchface.core

/**
 * Pure formatting and source-selection rules for the displayed heart rate.
 *
 * The face prefers a fresh live sensor reading. When none is available it falls
 * back to whatever the user placed in the heart-rate complication slot, and
 * finally to a placeholder so the layout never jumps.
 */
object HeartRateFormat {

    /** Placeholder shown when no heart rate is known. */
    const val PLACEHOLDER = "--"

    /** Unit label drawn next to the value. */
    const val BPM_LABEL = "BPM"

    /**
     * Formats a nullable BPM value for display.
     *
     * A null, zero or negative value becomes [PLACEHOLDER]; anything else is
     * rendered as its integer digits with no padding.
     */
    fun formatBpm(bpm: Int?): String =
        if (bpm == null || bpm <= 0) PLACEHOLDER else bpm.toString()

    /**
     * Picks the text to draw for the heart-rate slot.
     *
     * @param liveReading the most recent live sensor reading, if any.
     * @param complicationText text supplied by the complication fallback, if any.
     * @param nowMillis current wall-clock time, used for freshness.
     * @param maxAgeMillis how old a live reading may be before it is ignored.
     * @return the formatted BPM text, or [PLACEHOLDER] when neither source has a
     *   usable value.
     */
    fun displayText(
        liveReading: HeartRateReading?,
        complicationText: String?,
        nowMillis: Long,
        maxAgeMillis: Long = HeartRateReading.DEFAULT_MAX_AGE_MILLIS,
    ): String {
        if (liveReading != null && liveReading.isFresh(nowMillis, maxAgeMillis)) {
            return formatBpm(liveReading.valueBpm)
        }
        val fallback = complicationText?.trim()
        return if (fallback.isNullOrEmpty()) PLACEHOLDER else fallback
    }

    /**
     * Whether the displayed value came from the live sensor rather than the
     * complication. Useful for styling the live value more prominently.
     */
    fun isLive(
        liveReading: HeartRateReading?,
        nowMillis: Long,
        maxAgeMillis: Long = HeartRateReading.DEFAULT_MAX_AGE_MILLIS,
    ): Boolean = liveReading != null && liveReading.isFresh(nowMillis, maxAgeMillis)
}
