package com.aether.call.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Hardware accelerometer sensor listener detecting when the device is flipped face down
 * during an active incoming call to silence the ringer.
 */
class FlipToSilenceDetector(
    private val context: Context,
    private val onFlippedFaceDown: () -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isListening = false
    private var wasFaceUpInitially = false

    /**
     * Registers accelerometer sensor listener to start monitoring orientation.
     */
    fun startListening() {
        if (accelerometer != null && !isListening) {
            wasFaceUpInitially = false
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
        }
    }

    /**
     * Unregisters sensor listener.
     */
    fun stopListening() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Check if device is upright or tilted face-up
        if (z > 5.0f && abs(x) < 6.0f && abs(y) < 8.0f) {
            wasFaceUpInitially = true
        }

        // Check if device is resting flat face-down
        val isFaceDown = z < -7.5f && abs(x) < 4.0f && abs(y) < 4.0f

        if (isFaceDown && wasFaceUpInitially) {
            onFlippedFaceDown()
            stopListening()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
