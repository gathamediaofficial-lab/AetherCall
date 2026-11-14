package com.aether.call.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Combines hardware proximity sensor and ambient light sensor to detect when the device
 * is inside a pocket or bag during an incoming call, boosting ringer volume and vibration.
 */
class PocketModeDetector(
    private val context: Context,
    private val onPocketModeChanged: (isInPocket: Boolean) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var isListening = false
    private var isProximityNear = false
    private var isLightDark = false
    private var inPocket = false

    /**
     * Starts listening to proximity and light sensors.
     */
    fun startListening() {
        if (!isListening && sensorManager != null) {
            isProximityNear = false
            isLightDark = false
            inPocket = false

            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            lightSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            isListening = true
        }
    }

    /**
     * Stops listening to sensors.
     */
    fun stopListening() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensor = event?.sensor ?: return

        when (sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                isProximityNear = distance < maxRange && distance < 5f
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                isLightDark = lux < 5f // Low ambient light threshold (< 5 lux)
            }
        }

        val nowInPocket = isProximityNear && isLightDark
        if (nowInPocket != inPocket) {
            inPocket = nowInPocket
            onPocketModeChanged(inPocket)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
