package com.watchutil.watchface

import android.content.Context
import android.content.SharedPreferences
import com.watchutil.watchface.core.StatMetric

/**
 * Last-known-value cache for the six stat slots, persisted in app-private
 * [SharedPreferences].
 *
 * Writes only happen when the value actually changes, so the interactive render
 * loop does no I/O in the steady state; [SharedPreferences] keeps the map in
 * memory, making [read] cheap. Persistence matters because Wear OS may kill the
 * face process while the screen is off: on the next wake the slots start as
 * `NO_DATA`, and this supplies the last known text instead of flashing the
 * placeholder.
 *
 * A [SharedPreferences] instance needs no closing, so there is no resource to
 * release on the renderer's `onDestroy`.
 *
 * @param context any context; only its application context is retained.
 */
class StatValueStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The last value persisted for [metric], or `null` when none was ever
     * stored. Returned verbatim; trimming is the caller's concern.
     */
    fun read(metric: StatMetric): String? = prefs.getString(key(metric), null)

    /**
     * Persists [value] for [metric], returning true when the stored value
     * changed. A no-op (and `false`) when [value] already equals what is stored,
     * which keeps the steady-state render loop write-free.
     */
    fun write(metric: StatMetric, value: String): Boolean {
        if (prefs.getString(key(metric), null) == value) return false
        prefs.edit().putString(key(metric), value).apply()
        return true
    }

    private companion object {
        const val PREFS_NAME = "watchutil_stat_values"

        /** Stable per-metric preference key; [StatMetric.name] cannot change. */
        fun key(metric: StatMetric): String = "stat_${metric.name}"
    }
}
