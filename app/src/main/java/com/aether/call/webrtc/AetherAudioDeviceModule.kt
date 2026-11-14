package com.aether.call.webrtc

import android.content.Context
import com.aether.call.dsp.NeuralNoiseSuppressor
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom AudioDeviceModule wrapper integrating ONNX Neural Noise Suppression
 * into WebRTC's audio capture pipeline.
 */
class AetherAudioDeviceModule(
    context: Context,
    private val neuralNoiseSuppressor: NeuralNoiseSuppressor,
) {

    /**
     * Active noise cancellation intensity (0.0f = bypass, 1.0f = maximum suppression).
     */
    var ancIntensity: Float = 0.85f

    /**
     * WebRTC [AudioDeviceModule] instance with custom audio record callback.
     */
    val adm: AudioDeviceModule = JavaAudioDeviceModule.builder(context)
        .setSamplesReadyCallback { samples ->
            val data = samples.data
            if ((data != null) && (data.size >= 2)) {
                val shortCount = data.size / 2
                val pcmData = ShortArray(shortCount)

                // Read raw 16-bit PCM byte buffer into ShortArray (Little Endian)
                ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmData)

                // Apply neural noise suppression in-place
                neuralNoiseSuppressor.processFrame(pcmData, ancIntensity)

                // Write processed ShortArray back into byte buffer before WebRTC encoding
                ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmData)
            }
        }
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(false)
        .createAudioDeviceModule()

    /**
     * Releases underlying WebRTC Audio Device Module resources.
     */
    fun release() {
        adm.release()
    }
}