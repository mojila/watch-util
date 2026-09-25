package com.watchutil.watchface

import android.content.ComponentName
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
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.watchutil.watchface.core.StatMetric
import com.watchutil.watchface.core.slotBottom
import com.watchutil.watchface.core.slotLeft
import com.watchutil.watchface.core.slotRight
import com.watchutil.watchface.core.slotTop

/**
 * Watch face entry point.
 *
 * Registers one complication slot per [StatMetric] — six in a 2x3 grid — and
 * wires them into the real [WatchUtilRenderer]. Every slot is *read* by the
 * renderer rather than rendered by the library, so each metric appears whether
 * or not its provider draws into the slot. Low power is the governing
 * constraint; see [WatchUtilRenderer].
 */
class WatchUtilWatchFaceService : WatchFaceService() {

    /**
     * Builds the six metric complication slots.
     *
     * Each slot's bounds come from the `slot*` helpers in
     * `com.watchutil.watchface.core.StatGridLayout`, which return unit-square
     * display fractions — the form [ComplicationSlotBounds] expects. The grid
     * is laid out so it stays legible on round and square displays; the
     * round-bezel reasoning is documented on that file and is deliberately not
     * repeated here.
     */
    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): ComplicationSlotsManager {
        // Every slot defaults to the Xiaomi Fitness provider matching its
        // metric, because that is the only real source on the target watch.
        // This is a *default*, not a fixed data source: `setFixedComplication-
        // DataSource` is deliberately never called, so on any other watch the
        // user can pick a different provider (or none) in the editor.
        val slots = StatMetric.entries.map { metric ->
            // A single RectF per slot, replicated across every ComplicationType
            // by the library. The map-based constructors require an entry for
            // *all* types (not just the supported ones) or construction throws,
            // which makes the picker fail to add the face as a favorite.
            val bounds = ComplicationSlotBounds(
                RectF(
                    slotLeft(metric.column),
                    slotTop(metric.row),
                    slotRight(metric.column),
                    slotBottom(metric.row),
                ),
            )

            val policy = DefaultComplicationDataSourcePolicy(
                ComponentName(StatMetric.PROVIDER_PACKAGE, metric.providerService),
                ComplicationType.SHORT_TEXT,
                SystemDataSources.NO_DATA_SOURCE,
                ComplicationType.NOT_CONFIGURED,
            )

            ComplicationSlot.createRoundRectComplicationSlotBuilder(
                metric.slotId,
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
                policy,
                bounds,
            )
                .setEnabled(true)
                .setNameResourceId(R.string.watch_face_name)
                .build()
        }

        return ComplicationSlotsManager(slots, currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        // Resolve every slot up front so a registration bug fails loudly here
        // rather than silently dropping a metric from the grid.
        val statSlots: Map<StatMetric, ComplicationSlot> = StatMetric.entries.associateWith { metric ->
            requireNotNull(complicationSlotsManager.get(metric.slotId)) {
                "${metric.name} complication slot ${metric.slotId} was not registered"
            }
        }

        val renderer = WatchUtilRenderer(
            context = this,
            surfaceHolder = surfaceHolder,
            currentUserStyleRepository = currentUserStyleRepository,
            watchState = watchState,
            // Read for their text and drawn in the 2x3 grid, degrading to "--"
            // when a provider has no usable data.
            statSlots = statSlots,
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}
