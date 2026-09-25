package com.watchutil.watchface.core

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure time and date formatting for the watch face.
 *
 * The face has minute resolution only, so it renders at a low frame rate and
 * formats the current [ZonedDateTime] itself.
 */
object WatchFaceText {

    /**
     * Pattern for the short date, e.g. `"Fri, Sep 25"`.
     *
     * An explicit `"EEE, MMM d"` is used rather than
     * [java.time.format.FormatStyle.MEDIUM] because the localized style is not
     * guaranteed to stay short enough for the watch bezel in every locale, and
     * an explicit pattern keeps the shape stable while still translating the
     * day and month names. It is formatted with the requested locale, so tests
     * can pin `Locale.US` and assert an exact string.
     */
    private const val DATE_FORMATTER_PATTERN = "EEE, MMM d"

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
     * Formats a zone-aware instant as a short, locale-aware date.
     *
     * The zone carried by [zonedDateTime] is the one used: the date shown is
     * the date it is *there*, which is what the wearer sees on the watch.
     *
     * @param zonedDateTime the instant to format.
     * @param locale locale used for the day and month names; defaults to the
     *   device locale so the face follows the system language.
     * @return e.g. `"Fri, Sep 25"` in `Locale.US`.
     */
    fun date(
        zonedDateTime: ZonedDateTime,
        locale: Locale = Locale.getDefault(),
    ): String =
        DateTimeFormatter.ofPattern(DATE_FORMATTER_PATTERN, locale).format(zonedDateTime)

    private fun normalizeHour(hour: Int, is24Hour: Boolean): Int {
        val wrapped = ((hour % 24) + 24) % 24
        if (is24Hour) return wrapped
        val twelve = wrapped % 12
        return if (twelve == 0) 12 else twelve
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')
}
