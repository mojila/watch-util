package com.watchutil.watchface.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the `shouldAnimate()` contract. The library uses this as the gate for
 * entering ambient, so it must be false whenever the watch is ambient — the
 * bug this replaces deadlocked the face in interactive mode.
 */
class WatchFaceAnimationTest {

    @Test
    fun `visible and interactive animates`() {
        assertTrue(WatchFaceAnimation.shouldAnimate(isVisible = true, isAmbient = false))
    }

    @Test
    fun `ambient does not animate`() {
        assertFalse(WatchFaceAnimation.shouldAnimate(isVisible = true, isAmbient = true))
    }

    @Test
    fun `not visible does not animate`() {
        assertFalse(WatchFaceAnimation.shouldAnimate(isVisible = false, isAmbient = false))
        assertFalse(WatchFaceAnimation.shouldAnimate(isVisible = false, isAmbient = true))
    }

    @Test
    fun `null states do not animate`() {
        assertFalse(WatchFaceAnimation.shouldAnimate(isVisible = null, isAmbient = null))
        assertFalse(WatchFaceAnimation.shouldAnimate(isVisible = null, isAmbient = false))
        assertFalse(WatchFaceAnimation.shouldAnimate(isVisible = true, isAmbient = null))
    }
}
