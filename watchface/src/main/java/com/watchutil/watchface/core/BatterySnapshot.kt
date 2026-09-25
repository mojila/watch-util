package com.watchutil.watchface.core

/**
 * A cached snapshot of the watch's battery state.
 *
 * The watch face reads the sticky `ACTION_BATTERY_CHANGED` broadcast rather
 * than registering a permanent receiver, so this is refreshed only while the
 * interactive face is being drawn.
 *
 * @param levelPercent battery level clamped to `0..100`.
 * @param isCharging whether the device is currently charging.
 */
data class BatterySnapshot(
    val levelPercent: Int,
    val isCharging: Boolean,
) {
    /** The level as display text, e.g. `"82%"`. */
    fun formatLevel(): String = "$levelPercent%"

    /** The charging bolt indicator, or an empty string when not charging. */
    fun formatChargingSuffix(): String = if (isCharging) "⚡" else ""

    companion object {
        /** Shown until the first sticky broadcast is read. */
        val UNKNOWN = BatterySnapshot(levelPercent = 0, isCharging = false)

        /** Builds a snapshot from a raw level, clamping it into range. */
        fun fromRaw(level: Int, isCharging: Boolean): BatterySnapshot =
            BatterySnapshot(level.coerceIn(0, 100), isCharging)
    }
}
