package com.hemendra.depthwallpaper.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Converts device tilt into a smoothed, normalised offset in [-1, 1] on each
 * axis, relative to the orientation the phone was in when tracking started.
 *
 * The offset drives the parallax slide of the wallpaper layers. Uses the
 * accelerometer (gravity vector) which is available on effectively every device
 * and is cheap enough to run continuously.
 */
class ParallaxController(
    context: Context,
    private val onChanged: () -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile var offsetX = 0f
        private set
    @Volatile var offsetY = 0f
        private set

    private var hasNeutral = false
    private var neutralRoll = 0f
    private var neutralPitch = 0f

    fun start() {
        hasNeutral = false
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        offsetX = 0f
        offsetY = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Roll = left/right tilt, pitch = forward/back tilt (radians).
        val roll = Math.atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble())).toFloat()
        val pitch = Math.atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble())).toFloat()

        if (!hasNeutral) {
            neutralRoll = roll
            neutralPitch = pitch
            hasNeutral = true
        }

        val targetX = ((roll - neutralRoll) / MAX_TILT_RAD).coerceIn(-1f, 1f)
        val targetY = ((pitch - neutralPitch) / MAX_TILT_RAD).coerceIn(-1f, 1f)

        // Exponential smoothing so motion feels like liquid, not jitter.
        offsetX += (targetX - offsetX) * SMOOTHING
        offsetY += (targetY - offsetY) * SMOOTHING

        onChanged()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Tilt (≈29°) that maps to the maximum layer displacement. */
        const val MAX_TILT_RAD = 0.5f
        const val SMOOTHING = 0.12f
    }
}
