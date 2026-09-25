package com.watchutil.watchface.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.watchutil.watchface.core.HeartRateReading
import com.watchutil.watchface.core.HeartRateThrottle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Burst-samples the on-watch heart-rate sensor.
 *
 * Battery life is the primary constraint for the watch face, so this is
 * explicitly **not** a continuous stream:
 *
 *  - a burst is only started when [HeartRateThrottle] allows one and the face
 *    is interactive and visible;
 *  - the listener is unregistered as soon as the first plausible reading
 *    (`20..250` bpm) arrives, or immediately if the burst times out;
 *  - it is never left registered in ambient mode.
 *
 * If `BODY_SENSORS` has not been granted the source reports unavailable and the
 * UI falls back to the complication slot. The latest reading is exposed as a
 * [StateFlow] so the renderer can simply observe it.
 *
 * @param context any context; the application context is used internally.
 * @param throttle the timing decision, injected so it can be substituted in
 *   tests.
 */
class HeartRateSource(
    context: Context,
    private val throttle: HeartRateThrottle = HeartRateThrottle(),
) {
    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val heartRateSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _reading = MutableStateFlow<HeartRateReading?>(null)

    /** The most recent reading, or null when none has been taken yet. */
    val reading: StateFlow<HeartRateReading?> = _reading.asStateFlow()

    /** True when the device has a heart-rate sensor and the permission is held. */
    val isAvailable: Boolean
        get() = heartRateSensor != null && hasPermission()

    private var listener: SensorEventListener? = null
    private var burstStartedMillis = 0L
    private var finishedBurst = false

    /**
     * Whether the permission needed to read the sensor has been granted.
     */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Requests one sampling burst if the throttle allows it.
     *
     * @param nowMillis current wall-clock time in milliseconds.
     * @param isInteractive true only when the face is visible and interactive.
     * @param onReading invoked with the sampled bpm (or null when the burst
     *   failed/timed out) so the renderer can refresh immediately.
     * @return true when a burst was started.
     */
    fun requestSample(
        nowMillis: Long,
        isInteractive: Boolean,
        onReading: (Int?) -> Unit,
    ): Boolean {
        if (!isInteractive) {
            stop()
            return false
        }
        if (!isAvailable) return false
        if (!throttle.shouldSample(nowMillis, isInteractive)) return false

        startBurst(nowMillis, onReading)
        return true
    }

    /**
     * Gives up on a burst that has run longer than [BURST_TIMEOUT_MILLIS]
     * without producing a plausible value. Call once per interactive draw.
     */
    fun cancelIfTimedOut(nowMillis: Long) {
        if (finishedBurst) return
        if (listener != null && nowMillis - burstStartedMillis > BURST_TIMEOUT_MILLIS) {
            finishedBurst = true
            stop()
        }
    }

    /** Unregisters the listener; safe to call at any time. */
    fun stop() {
        val current = listener ?: return
        sensorManager?.unregisterListener(current)
        listener = null
    }

    private fun startBurst(nowMillis: Long, onReading: (Int?) -> Unit) {
        val manager = sensorManager ?: return
        val sensor = heartRateSensor ?: return

        // Defensive: never stack listeners if a previous burst is still running.
        stop()

        burstStartedMillis = nowMillis
        finishedBurst = false

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
                val bpm = event.values.firstOrNull()?.toInt() ?: return
                if (bpm !in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM) return

                finishedBurst = true
                _reading.value = HeartRateReading(bpm, System.currentTimeMillis())
                stop()
                onReading(bpm)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        listener = newListener
        manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private companion object {
        /** Below this a pulse reading is noise rather than a person. */
        const val MIN_PLAUSIBLE_BPM = 20

        /** Above this the sensor is almost certainly mis-reading. */
        const val MAX_PLAUSIBLE_BPM = 250

        /** Give up on a burst after this long so the listener never lingers. */
        const val BURST_TIMEOUT_MILLIS = 15_000L
    }
}
