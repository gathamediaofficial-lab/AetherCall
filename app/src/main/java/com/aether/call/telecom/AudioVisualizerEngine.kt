package com.aether.call.telecom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lightweight, non-blocking audio amplitude provider computing PCM RMS values and simulating voice wave pulses.
 */
object AudioVisualizerEngine {

    private val _rawAmplitude = MutableStateFlow(0f)
    val rawAmplitude: StateFlow<Float> = _rawAmplitude.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pulseJob: Job? = null

    /**
     * Computes RMS amplitude from incoming 16-bit PCM audio buffer.
     */
    fun feedPcmBuffer(buffer: ShortArray, readSize: Int) {
        if (readSize <= 0 || buffer.isEmpty()) return

        engineScope.launch {
            var sum = 0.0
            val size = readSize.coerceAtMost(buffer.size)
            for (i in 0 until size) {
                val sample = buffer[i]
                sum += (sample * sample).toDouble()
            }
            val rms = sqrt(sum / size.coerceAtLeast(1))
            val normalized = (rms / 32768.0).toFloat().coerceIn(0.05f, 1.0f)
            _rawAmplitude.value = normalized
        }
    }

    /**
     * Simulates dynamic random-walk voice pulses for active calls when PCM hardware capture is restricted.
     */
    fun simulateVoicePulse() {
        if (pulseJob?.isActive == true) return

        pulseJob = engineScope.launch {
            var currentPulse = 0.3f
            while (isActive) {
                delay(50)
                val delta = Random.nextFloat() * 0.2f - 0.1f
                currentPulse = (currentPulse + delta).coerceIn(0.15f, 0.85f)
                _rawAmplitude.value = currentPulse
            }
        }
    }

    /**
     * Resets amplitude to 0f and cancels active emission jobs.
     */
    fun stop() {
        pulseJob?.cancel()
        pulseJob = null
        _rawAmplitude.value = 0f
    }
}
