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
import com.watchutil.watchface.core.HeartRateFormat
import com.watchutil.watchface.core.WatchFaceAnimation
import com.watchutil.watchface.core.WatchFaceText
import com.watchutil.watchface.sensor.HeartRateSource
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The real WatchUtil watch face.
 *
 * Design constraints, in priority order:
 *
 *  1. **Low power.** The renderer is created with a 1 s interactive delay, since
 *     the face only needs whole-second resolution, and returns false from
 *     [shouldAnimate] whenever the drawn content is not changing. The
 *     heart-rate sensor is burst-sampled (see [HeartRateSource]) and never in
 *     ambient.
 *  2. **Legibility.** Time is the primary element; battery and heart rate sit
 *     below it, clear of the corners so round and square displays both work.
 *  3. **Ambient safety.** Ambient / AOD draws only time and battery, in a dim
 *     gray, with thin outlines and a burn-in shift.
 *
 * The heart-rate complication slot is rendered by this renderer via
 * [ComplicationSlot.render] so that taps and the highlight layer keep working,
 * and so the fallback text is visible whenever the live sensor value is stale
 * or unavailable.
 *
 * @param heartRateSlot the fallback slot, resolved from the service's manager.
 */
class WatchUtilRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val watchState: WatchState,
    private val heartRateSlot: ComplicationSlot,
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
        lateinit var labelPaint: Paint
        lateinit var labelOutlinePaint: Paint
        lateinit var valuePaint: Paint

        override fun onDestroy() = Unit
    }

    private val heartRateSource = HeartRateSource(context.applicationContext)

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

        // Defense in depth: only ever sample when the watch is genuinely
        // interactive. `watchState.isAmbient` may lag the draw mode, so treat a
        // null as "do not sample".
        val nowWall = System.currentTimeMillis()
        val interactive = watchState.isAmbient.value != true
        heartRateSource.requestSample(nowWall, isInteractive = interactive) { invalidate() }
        // Bound the burst's life so the listener never lingers when the sensor
        // produces nothing (e.g. the watch is not on a wrist).
        heartRateSource.cancelIfTimedOut(nowWall)

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = minOf(bounds.width(), bounds.height()) / 2f

        // Time: large, centered slightly above the middle to leave room for the
        // stats and the heart rate below.
        assets.timePaint.textSize = radius * TIME_TEXT_RATIO
        val timeText = WatchFaceText.timeWithSeconds(
            zonedDateTime.hour,
            zonedDateTime.minute,
            zonedDateTime.second,
            is24Hour = true,
        )
        val timeY = centerY - radius * TIME_Y_RATIO
        canvas.drawText(timeText, centerX, timeY, assets.timePaint)

        // Battery under the time: a small label and the percentage.
        val statSize = radius * STAT_TEXT_RATIO
        assets.labelPaint.textSize = statSize * 0.7f
        assets.valuePaint.textSize = statSize

        val batteryLabelY = centerY + radius * STAT_Y_RATIO
        canvas.drawText("BATTERY", centerX, batteryLabelY, assets.labelPaint)
        canvas.drawText(
            battery.formatLevel(),
            centerX,
            batteryLabelY + statSize * 1.3f,
            assets.valuePaint,
        )

        // Heart rate: exactly one of live or complication is drawn, so the two
        // can never overlap in the lower-centre region.
        //  - fresh live reading -> draw the BPM directly (taps/highlight still
        //    work via renderHighlightLayer, which always delegates to the slot);
        //  - complication has usable data -> render the slot as the fallback;
        //  - neither -> draw our own "--" placeholder, so nothing overlaps and
        //    the region is never left blank.
        val liveReading = heartRateSource.reading.value
        // Use the unit-tested helper for the freshness decision so the tested
        // rule is the one that actually ships.
        val live = HeartRateFormat.isLive(liveReading, nowWall)
        if (live) {
            val hrY = centerY + radius * HR_Y_RATIO
            assets.valuePaint.textSize = statSize * 1.4f
            assets.valuePaint.color = HR_COLOR
            canvas.drawText(
                HeartRateFormat.formatBpm(liveReading?.valueBpm),
                centerX,
                hrY,
                assets.valuePaint,
            )
            assets.valuePaint.color = VALUE_COLOR
        } else if (!complicationText().isNullOrBlank()) {
            heartRateSlot.render(canvas, zonedDateTime, renderParameters)
        } else {
            val hrY = centerY + radius * HR_Y_RATIO
            assets.valuePaint.textSize = statSize * 1.4f
            assets.valuePaint.color = LABEL_COLOR
            canvas.drawText(
                HeartRateFormat.PLACEHOLDER,
                centerX,
                hrY,
                assets.valuePaint,
            )
            assets.valuePaint.color = VALUE_COLOR
        }
    }

    /**
     * Extracts the heart-rate text from the complication slot, or null when the
     * slot has no usable data. Handles `NO_DATA` / `EMPTY` / `NOT_CONFIGURED` /
     * `NO_PERMISSION` by returning null so the caller can show `"--"`.
     */
    private fun complicationText(): String? {
        val data: ComplicationData = heartRateSlot.complicationData.value
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
        // Ambient runs no heart-rate sampling; stop any burst in flight so the
        // sensor listener cannot outlive the interactive-to-ambient transition.
        heartRateSource.stop()

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
        // Delegate to the slot so a tapped complication shows its highlight.
        heartRateSlot.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
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
        heartRateSource.stop()
    }

    private companion object {
        /**
         * Whole-second resolution is all the face renders, so 1 s is the
         * lowest frame rate that still looks live. Never 16 ms.
         */
        const val INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS = 1000L

        /** How often the sticky battery broadcast is re-read while visible. */
        const val BATTERY_REFRESH_MILLIS = 60_000L

        /** Outline width for ambient text; thin strokes stay dim. */
        const val AMBIENT_STROKE_WIDTH = 2f

        const val TIME_TEXT_RATIO = 0.40f
        const val STAT_TEXT_RATIO = 0.10f
        const val TIME_Y_RATIO = 0.22f
        const val STAT_Y_RATIO = 0.26f
        const val HR_Y_RATIO = 0.62f
        const val AMBIENT_TIME_TEXT_RATIO = 0.34f
        const val AMBIENT_BATTERY_Y_RATIO = 0.20f

        // Matches the :app Wear Material 3 scheme (primary blue, light text).
        val TIME_COLOR = Color.parseColor("#E2E2E6")
        val VALUE_COLOR = Color.parseColor("#4FC3F7")
        val HR_COLOR = Color.parseColor("#FFB74D")
        val LABEL_COLOR = Color.parseColor("#8C9199")

        /** Dim gray keeps ambient comfortable and saves power. */
        val AMBIENT_DIM_COLOR = Color.parseColor("#808080")
    }
}
