package com.watchutil.watchface

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.SystemClock
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.watchutil.watchface.core.BatterySnapshot
import com.watchutil.watchface.core.FIRST_LABEL_Y_RATIO
import com.watchutil.watchface.core.STAT_TEXT_RATIO
import com.watchutil.watchface.core.StatFormat
import com.watchutil.watchface.core.StatMetric
import com.watchutil.watchface.core.StatValueCache
import com.watchutil.watchface.core.WatchFaceAnimation
import com.watchutil.watchface.core.WatchFaceText
import com.watchutil.watchface.core.columnX
import com.watchutil.watchface.core.labelTextSize
import com.watchutil.watchface.core.labelY
import com.watchutil.watchface.core.statTextSize
import com.watchutil.watchface.core.valueY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The real WatchUtil watch face.
 *
 * Design constraints, in priority order:
 *
 *  1. **Low power.** The face has minute resolution (no seconds), so the
 *     renderer is created with a 60 s interactive delay, and returns false from
 *     [shouldAnimate] whenever the drawn content is not changing. Complication
 *     values still refresh promptly, because each slot's data flow calls
 *     `invalidate()` when a new value arrives.
 *  2. **Legibility.** Time is the primary element; a combined date+battery line
 *     sits beneath it, and the six health metrics fill a 2x3 grid below that.
 *     Grid geometry comes from the `core` grid helpers, which keep every cell
 *     clear of the round bezel and the square corners.
 *  3. **Ambient safety.** Ambient / AOD draws only time and battery, in a dim
 *     gray, with thin outlines and a burn-in shift.
 *
 * The six metric slots are *read* (never rendered by the library) and their text
 * is drawn directly in the grid; a slot with no usable data contributes
 * [StatFormat.PLACEHOLDER]. Because an unrendered slot gets no frames of its
 * own, this renderer observes [ComplicationSlot.complicationData] for **every**
 * slot and calls [postInvalidate] when it changes, so a new value appears
 * immediately instead of only on the next scheduled frame.
 *
 * @param statSlots one slot per [StatMetric], resolved from the service's
 *   manager and keyed by the metric it feeds.
 */
class WatchUtilRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val watchState: WatchState,
    private val statSlots: Map<StatMetric, ComplicationSlot>,
) : Renderer.CanvasRenderer2<WatchUtilRenderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    // Software canvas: the face is text-heavy, so a hardware canvas buys nothing
    // and costs a surface per frame.
    CanvasType.SOFTWARE,
    INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS,
    false,
) {

    /** Paints and typefaces built once in [createSharedAssets]. */
    class SharedAssets : Renderer.SharedAssets {
        lateinit var timePaint: Paint
        lateinit var timeOutlinePaint: Paint
        lateinit var datePaint: Paint
        lateinit var labelPaint: Paint
        lateinit var labelOutlinePaint: Paint
        lateinit var valuePaint: Paint

        override fun onDestroy() = Unit
    }

    /**
     * Owns the [statSlots] observers for the renderer's lifetime; cancelled in
     * [onDestroy] so nothing leaks past the face.
     *
     * `Dispatchers.Main.immediate` is deliberate: slot data arrives on the UI
     * thread, and an immediate dispatch keeps the invalidation on that thread
     * without an extra hop. [postInvalidate] is used anyway so the observer is
     * safe even if it is ever dispatched elsewhere.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Last-known values persisted across a process death. Wear OS can kill the
     * face while the screen is off; without this, a recreated process draws the
     * placeholder for every slot until the providers respond. Only the
     * interactive grid consults it — ambient draws time + battery only.
     */
    private val statValueStore = StatValueStore(context)

    init {
        // An unrendered complication slot never gets a frame of its own, so a
        // new metric value would otherwise only appear on the next 60 s
        // scheduled frame. The library's invalidate callback for a slot is
        // driven by its CanvasComplication (see ComplicationSlot.renderer), but
        // for a read-only slot nothing renders it, so the data flow is observed
        // directly — for every slot, so any metric updating redraws the grid.
        // complicationData emits for both new data and timeline selection, which
        // is exactly when a drawn number can change.
        statSlots.values.forEach { slot ->
            scope.launch {
                slot.complicationData.collect { postInvalidate() }
            }
        }
    }

    /** Battery is refreshed at most once a minute; it changes slowly. */
    private var cachedBattery: BatterySnapshot = BatterySnapshot.UNKNOWN
    private var lastBatteryRefreshMillis = 0L

    override suspend fun createSharedAssets(): SharedAssets {
        val assets = SharedAssets()
        val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val bold = Typeface.create(regular, Typeface.BOLD)

        assets.timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            color = TIME_COLOR
        }
        // Ambient uses outline text so the face stays dim and safe under both
        // low-bit ambient and burn-in protection.
        assets.timeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = AMBIENT_STROKE_WIDTH
            color = AMBIENT_DIM_COLOR
        }
        assets.datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textAlign = Paint.Align.CENTER
            color = DATE_COLOR
        }
        assets.labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textAlign = Paint.Align.CENTER
            color = LABEL_COLOR
        }
        assets.labelOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = AMBIENT_STROKE_WIDTH
            color = AMBIENT_DIM_COLOR
        }
        assets.valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textAlign = Paint.Align.CENTER
            color = VALUE_COLOR
        }
        return assets
    }

    /**
     * The library's gate for entering ambient.
     *
     * `WatchFaceImpl.maybeUpdateDrawMode()` sets `DrawMode.AMBIENT` **only** when
     * `watchState.isAmbient` is true *and* this method returns false; if this
     * method returns true, the face is kept in interactive mode. It is therefore
     * the cause of ambient, not a consequence of it, and must never be derived
     * from [renderParameters.drawMode] — doing so deadlocks the face in
     * interactive mode, because ambient would only be entered once the draw mode
     * were already ambient.
     *
     * `StateFlow<Boolean>` values are nullable, so a null is treated as false
     * (not animating), which is the safe default for power. The decision itself
     * lives in [WatchFaceAnimation.shouldAnimate] so it is unit-tested.
     */
    override fun shouldAnimate(): Boolean = WatchFaceAnimation.shouldAnimate(
        isVisible = watchState.isVisible.value,
        isAmbient = watchState.isAmbient.value,
    )

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets,
    ) {
        // CanvasRenderer2 never clears the backing surface between frames, so
        // without this the new frame composites over the previous one (ghosted
        // time/battery, and both interactive and ambient layers visible).
        canvas.drawColor(Color.BLACK)

        // drawMode is authoritative; watchState.isAmbient lags the draw mode.
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT ||
            renderParameters.drawMode == DrawMode.MUTE

        if (ambient) {
            renderAmbient(canvas, bounds, zonedDateTime, sharedAssets)
        } else {
            renderInteractive(canvas, bounds, zonedDateTime, sharedAssets)
        }
    }

    // ------------------------------------------------------------- interactive

    private fun renderInteractive(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        assets: SharedAssets,
    ) {
        val battery = refreshBatteryIfDue()

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = minOf(bounds.width(), bounds.height()) / 2f

        // Time: large, centered in the upper third. No seconds are drawn; the
        // minute-resolution update delay lives in the companion object.
        assets.timePaint.textSize = radius * TIME_TEXT_RATIO
        val timeText = WatchFaceText.time(
            zonedDateTime.hour,
            zonedDateTime.minute,
            is24Hour = true,
        )
        val timeY = centerY - radius * TIME_Y_RATIO
        canvas.drawText(timeText, centerX, timeY, assets.timePaint)

        // Date and battery share one line directly under the time, in the same
        // paint so it reads as part of the time block rather than as another
        // stat. Battery used to have a grid cell of its own; folding it in here
        // frees that cell for the six metrics.
        assets.datePaint.textSize = radius * DATE_TEXT_RATIO
        canvas.drawText(
            "${WatchFaceText.date(zonedDateTime)} · ${battery.formatLevel()}",
            centerX,
            centerY - radius * DATE_Y_RATIO,
            assets.datePaint,
        )

        // The 2x3 metric grid. Text sizes are constant across the grid, so they
        // are set once here rather than per metric.
        assets.labelPaint.textSize = labelTextSize(radius)
        assets.valuePaint.textSize = statTextSize(radius)
        assets.valuePaint.color = VALUE_COLOR

        for (metric in StatMetric.entries) {
            val slot = statSlots.getValue(metric)
            val x = columnX(centerX, radius, metric.column)
            canvas.drawText(
                metric.label,
                x,
                labelY(centerY, radius, metric.row),
                assets.labelPaint,
            )
            // The provider's text is drawn through the persistent cache: a
            // usable value wins and is written back, while an unusable slot
            // (e.g. right after the process was recreated) falls back to the
            // last known value instead of flashing the placeholder. The slot is
            // never rendered, so its invalidation is wired in `init`.
            val current = complicationText(slot)
            StatValueCache.persistable(current)?.let { statValueStore.write(metric, it) }
            canvas.drawText(
                StatValueCache.resolve(current, statValueStore.read(metric)),
                x,
                valueY(centerY, radius, metric.row),
                assets.valuePaint,
            )
        }
    }

    /**
     * Extracts the display text from [slot], or null when the slot has no usable
     * data. Handles `NO_DATA` / `EMPTY` / `NOT_CONFIGURED` / `NO_PERMISSION` by
     * returning null so the caller can show its own placeholder.
     */
    private fun complicationText(slot: ComplicationSlot): String? {
        val data: ComplicationData = slot.complicationData.value
        val instant: Instant = Instant.now()
        return when (data.type) {
            ComplicationType.SHORT_TEXT ->
                (data as? ShortTextComplicationData)
                    ?.text
                    ?.getTextAt(context.resources, instant)
                    ?.toString()
            ComplicationType.RANGED_VALUE -> {
                val ranged = data as? RangedValueComplicationData
                ranged?.text?.getTextAt(context.resources, instant)?.toString()
                    ?: ranged?.value?.toInt()?.toString()
            }
            ComplicationType.NO_DATA,
            ComplicationType.EMPTY,
            ComplicationType.NOT_CONFIGURED,
            ComplicationType.NO_PERMISSION,
            -> null
            else -> null
        }
    }

    // ---------------------------------------------------------------- ambient

    private fun renderAmbient(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        assets: SharedAssets,
    ) {
        // No seconds element is drawn, so the system may idle between minutes.
        // Time and battery only.
        val battery = refreshBatteryIfDue()

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = minOf(bounds.width(), bounds.height()) / 2f

        // Burn-in protection: shift the content by a pixel. The offset is
        // derived from the current minute so it changes slowly.
        val shift = if (watchState.hasBurnInProtection) {
            (zonedDateTime.minute % 3 - 1).toFloat()
        } else {
            0f
        }

        // Low-bit ambient displays can only render a few colors per pixel, so
        // disable anti-aliasing to avoid ugly dithering artifacts.
        if (watchState.hasLowBitAmbient) {
            assets.timeOutlinePaint.isAntiAlias = false
            assets.labelOutlinePaint.isAntiAlias = false
        }

        assets.timeOutlinePaint.textSize = radius * AMBIENT_TIME_TEXT_RATIO
        val timeText = WatchFaceText.time(
            zonedDateTime.hour,
            zonedDateTime.minute,
            is24Hour = true,
        )
        canvas.drawText(
            timeText,
            centerX + shift,
            centerY + shift,
            assets.timeOutlinePaint,
        )

        assets.labelOutlinePaint.textSize = radius * STAT_TEXT_RATIO * 0.8f
        canvas.drawText(
            battery.formatLevel(),
            centerX + shift,
            centerY + radius * AMBIENT_BATTERY_Y_RATIO + shift,
            assets.labelOutlinePaint,
        )
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets,
    ) {
        // Delegate to every slot so a tapped complication shows its highlight.
        // The slots are never rendered, but a user can still tap them in the
        // editor, so their highlights must be painted too.
        statSlots.values.forEach { slot ->
            slot.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
        }
    }

    // --------------------------------------------------------------- battery

    /**
     * Reads the sticky `ACTION_BATTERY_CHANGED` broadcast, at most once a
     * minute. Passing a null receiver returns the current value immediately
     * without registering a permanent receiver.
     */
    private fun refreshBatteryIfDue(): BatterySnapshot {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBatteryRefreshMillis < BATTERY_REFRESH_MILLIS) {
            return cachedBattery
        }
        lastBatteryRefreshMillis = now
        cachedBattery = try {
            val intent: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            if (intent == null) {
                cachedBattery
            } else {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val percent = if (scale > 0) level * 100 / scale else level
                BatterySnapshot.fromRaw(percent, charging)
            }
        } catch (_: Exception) {
            cachedBattery
        }
        return cachedBattery
    }

    override fun onDestroy() {
        scope.cancel()
    }

    private companion object {
        /**
         * The face has minute resolution only (seconds were removed), so the
         * scheduler only needs to wake once a minute. Complication values still
         * appear immediately, because each slot's data flow calls
         * `invalidate()` when a new value arrives. Never 16 ms.
         */
        const val INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS = 60_000L

        /** How often the sticky battery broadcast is re-read while visible. */
        const val BATTERY_REFRESH_MILLIS = 60_000L

        /** Outline width for ambient text; thin strokes stay dim. */
        const val AMBIENT_STROKE_WIDTH = 2f

        /**
         * Vertical layout, as a fraction of [radius] above/below centre. The
         * time and date block sits in the upper third; the 2x3 grid below it is
         * positioned entirely by the `core` grid helpers, whose first label
         * baseline ([FIRST_LABEL_Y_RATIO]) clears the date at [DATE_Y_RATIO].
         */
        const val TIME_TEXT_RATIO = 0.40f
        const val TIME_Y_RATIO = 0.22f
        const val DATE_TEXT_RATIO = 0.09f
        const val DATE_Y_RATIO = 0.10f

        const val AMBIENT_TIME_TEXT_RATIO = 0.34f
        const val AMBIENT_BATTERY_Y_RATIO = 0.20f

        // Matches the :app Wear Material 3 scheme (primary blue, light text).
        val TIME_COLOR = Color.parseColor("#E2E2E6")
        val DATE_COLOR = Color.parseColor("#B9BEC6")
        val VALUE_COLOR = Color.parseColor("#4FC3F7")
        val LABEL_COLOR = Color.parseColor("#8C9199")

        /** Dim gray keeps ambient comfortable and saves power. */
        val AMBIENT_DIM_COLOR = Color.parseColor("#808080")
    }
}
