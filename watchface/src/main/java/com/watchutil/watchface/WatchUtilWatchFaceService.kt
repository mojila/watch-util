package com.watchutil.watchface

import android.graphics.RectF
import android.view.SurfaceHolder
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository

/**
 * Watch face entry point.
 *
 * Wires up the heart-rate complication fallback slot and the real
 * [WatchUtilRenderer]. Low power is the governing constraint: see
 * [WatchUtilRenderer] and [com.watchutil.watchface.sensor.HeartRateSource].
 */
class WatchUtilWatchFaceService : WatchFaceService() {

    /**
     * Builds the heart-rate fallback slot.
     *
     * The slot is placed in the lower-centre area, below the time and clear of
     * the corners so it is legible on round and square displays. Its bounds use
     * fractional unit-square coordinates.
     */
    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): ComplicationSlotsManager {
        // A single RectF for the slot, replicated across every ComplicationType
        // by the library. The map-based constructors require an entry for *all*
        // types (not just the supported ones) or construction throws, which
        // makes the picker fail to add the face as a favorite.
        val bounds = ComplicationSlotBounds(
            RectF(
                HEART_RATE_LEFT,
                HEART_RATE_TOP,
                HEART_RATE_RIGHT,
                HEART_RATE_BOTTOM,
            ),
        )

        val slot = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            HEART_RATE_SLOT_ID,
            { watchState, invalidateCallback ->
                CanvasComplicationDrawable(
                    ComplicationDrawable(this),
                    watchState,
                    invalidateCallback,
                )
            },
            listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
            ),
            DefaultComplicationDataSourcePolicy(),
            bounds,
        )
            .setEnabled(true)
            .setNameResourceId(R.string.watch_face_name)
            .build()

        return ComplicationSlotsManager(listOf(slot), currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        val heartRateSlot: ComplicationSlot =
            requireNotNull(complicationSlotsManager.get(HEART_RATE_SLOT_ID)) {
                "Heart-rate complication slot $HEART_RATE_SLOT_ID was not registered"
            }

        val renderer = WatchUtilRenderer(
            context = this,
            surfaceHolder = surfaceHolder,
            currentUserStyleRepository = currentUserStyleRepository,
            watchState = watchState,
            heartRateSlot = heartRateSlot,
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }

    private companion object {
        /** Id of the single heart-rate fallback slot. */
        const val HEART_RATE_SLOT_ID = 1

        // Lower-centre, fraction of the display. Kept off the edges so the
        // content is clear of the corners on a square display and does not wrap
        // around the bezel on a round one.
        const val HEART_RATE_LEFT = 0.35f
        const val HEART_RATE_TOP = 0.62f
        const val HEART_RATE_RIGHT = 0.65f
        const val HEART_RATE_BOTTOM = 0.80f
    }
}
