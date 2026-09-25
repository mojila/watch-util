package com.watchutil.watchface.core

/**
 * Pure decision for [androidx.wear.watchface.Renderer.shouldAnimate].
 *
 * The wear watch-face library calls `shouldAnimate()` as the **gate for
 * entering ambient** (`WatchFaceImpl.maybeUpdateDrawMode()` sets
 * `DrawMode.AMBIENT` only when the watch is ambient *and* this returns false).
 * It must therefore key off the watch state, never off the current draw mode:
 * deriving it from the draw mode deadlocks the face in interactive mode,
 * because ambient would only be entered once the draw mode were already
 * ambient.
 *
 * Kept free of Android imports so the contract can be pinned by a JVM test.
 */
object WatchFaceAnimation {

    /**
     * @param isVisible `watchState.isVisible.value`, nullable from the flow.
     * @param isAmbient `watchState.isAmbient.value`, nullable from the flow.
     * @return true only when the watch is genuinely visible and interactive.
     *   A null is treated as false so an unknown state never animates (and
     *   therefore never blocks entering ambient).
     */
    fun shouldAnimate(isVisible: Boolean?, isAmbient: Boolean?): Boolean =
        isVisible == true && isAmbient == false
}
