package com.watchutil.watchface.core

/**
 * Resolves the text a stat slot should draw when the provider may have no data
 * yet, using a persisted last-known value as the fallback.
 *
 * The face process usually survives screen-off, so the slots' `StateFlow`
 * retains its values across a normal wake. But Wear OS can kill the process
 * while the screen is off; on recreation every slot starts as `NO_DATA` and the
 * grid would flash [StatFormat.PLACEHOLDER] until the providers respond. This
 * resolves against a value persisted on a previous run instead.
 *
 * The two concerns are deliberately separated:
 *
 *  - [resolve] decides the text to draw for one frame.
 *  - [persistable] normalizes the value worth writing back, or `null` when there
 *    is nothing new to persist (so the placeholder never overwrites a good
 *    cached value).
 *
 * Pure logic — no Android imports, unit-tested on the JVM.
 */
object StatValueCache {

    /**
     * The text to draw for [metric] given the provider text this frame and the
     * last persisted value.
     *
     * A usable [current] wins; otherwise a non-blank [cached] is shown; if both
     * are unusable the placeholder is returned.
     *
     * @param current raw provider text for this frame, or `null`.
     * @param cached last known value persisted for the slot, or `null`.
     */
    fun resolve(current: String?, cached: String?): String {
        val resolvedCurrent = StatFormat.value(current)
        if (resolvedCurrent != StatFormat.PLACEHOLDER) return resolvedCurrent
        val resolvedCached = StatFormat.value(cached)
        return if (resolvedCached != StatFormat.PLACEHOLDER) {
            resolvedCached
        } else {
            StatFormat.PLACEHOLDER
        }
    }

    /**
     * The value to persist for the slot, or `null` when there is nothing worth
     * writing.
     *
     * Only a usable value (one that does not collapse to
     * [StatFormat.PLACEHOLDER]) is persistable, so a transient loss of provider
     * data never clobbers the cache.
     *
     * @param current raw provider text for this frame, or `null`.
     */
    fun persistable(current: String?): String? {
        val resolved = StatFormat.value(current)
        return if (resolved != StatFormat.PLACEHOLDER) resolved else null
    }
}
