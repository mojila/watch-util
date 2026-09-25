package com.watchutil.watchface.core

/**
 * Text formatting shared by the six stat slots.
 *
 * Complication text arrives as a nullable, possibly padded string. The face
 * never draws an empty value, so blank input collapses to [PLACEHOLDER].
 *
 * Pure logic — no Android imports, unit-tested on the JVM.
 */
object StatFormat {

    /** Shown when a slot has no usable data. */
    const val PLACEHOLDER = "--"

    /** Trims provider text; null/blank becomes [PLACEHOLDER]. */
    fun value(text: String?): String {
        val trimmed = text?.trim()
        return if (trimmed.isNullOrEmpty()) PLACEHOLDER else trimmed
    }
}
