package com.watchutil.watchface.core

/**
 * Pure time formatting for the watch face.
 *
 * The face only needs whole-second resolution, so it renders at a low frame
 * rate and formats the current [java.time.ZonedDateTime] itself.
 */
object WatchFaceText {

    /**
     * Formats `hour:minute` as `HH:mm` (24-hour) or `h:mm` (12-hour).
     *
     * @param hour hour of day, 0..23.
     * @param minute minute of hour, 0..59.
     * @param is24Hour true for 24-hour time, false for 12-hour with no AM/PM
     *   marker (the watch face relies on the system's own indicator).
     */
    fun time(hour: Int, minute: Int, is24Hour: Boolean): String {
        val h = normalizeHour(hour, is24Hour)
        return "${pad(h)}:${pad(minute)}"
    }

    /**
     * Formats `hour:minute:second`.
     *
     * Seconds are zero-padded in both modes; the hour is zero-padded only in
     * 24-hour mode so the display stays stable as the leading digit changes.
     */
    fun timeWithSeconds(hour: Int, minute: Int, second: Int, is24Hour: Boolean): String {
        val h = normalizeHour(hour, is24Hour)
        return "${pad(h)}:${pad(minute)}:${pad(second)}"
    }

    private fun normalizeHour(hour: Int, is24Hour: Boolean): Int {
        val wrapped = ((hour % 24) + 24) % 24
        if (is24Hour) return wrapped
        val twelve = wrapped % 12
        return if (twelve == 0) 12 else twelve
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')
}
