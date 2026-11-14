package com.aether.call.dsp

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.InputStream

/**
 * On-Device Neural Noise Suppressor using ONNX Runtime.
 *
 * Processes PCM audio frames to suppress background noise. Fallbacks gracefully
 * to pure bypass mode if the ONNX model asset ("deep_denoise_stream.onnx") is missing
 * or fails to initialize.
 */
class NeuralNoiseSuppressor(context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    var isModelLoaded: Boolean = false
        private set

    // Recurrent hidden state vector (size 128) persisted across audio frames
    private val hiddenState = FloatArray(128)

    init {
        try {
            val modelBytes = context.assets.open("deep_denoise_stream.onnx").use { stream: InputStream ->
                stream.readBytes()
            }
            env = OrtEnvironment.getEnvironment()
            session = env?.createSession(modelBytes, OrtSession.SessionOptions())
            isModelLoaded = session != null
        } catch (e: Exception) {
            Log.w("NeuralNoiseSuppressor", "ONNX model asset 'deep_denoise_stream.onnx' missing or init failed. Running in pure bypass mode.", e)
            isModelLoaded = false
            session = null
            env = null
        }
    }

    /**
     * Processes an audio frame in-place.
     *
     * @param pcmData 16-bit PCM audio samples modified in-place.
     * @param suppressionLevel Blending factor from 0.0 (raw audio) to 1.0 (full noise suppression).
     */
    fun processFrame(pcmData: ShortArray, suppressionLevel: Float) {
        val frameSize = pcmData.size
        if ((frameSize == 0) || (!isModelLoaded) || (session == null) || (env == null)) {
            // Pure bypass mode: leave pcmData untouched
            return
        }

        // 1. Normalize PCM ShortArray to FloatArray in [-1.0, 1.0]
        val normalizedInput = FloatArray(frameSize) { i ->
            pcmData[i] / 32768.0f
        }

        val denoisedFloat = FloatArray(frameSize)
        var inferenceSuccess = false

        // 2. Run inference if ONNX session is active
        try {
            val currentEnv = env!!
            val currentSession = session!!

            val inputAudioTensor = OnnxTensor.createTensor(currentEnv, arrayOf(normalizedInput))
            val hiddenInTensor = OnnxTensor.createTensor(currentEnv, arrayOf(hiddenState))

            val inputNames = currentSession.inputNames
            val inputs = mutableMapOf<String, OnnxTensor>()

            if (inputNames.size >= 2) {
                val audioKey = inputNames.elementAt(0)
                val hiddenKey = inputNames.elementAt(1)
                inputs[audioKey] = inputAudioTensor
                inputs[hiddenKey] = hiddenInTensor
            } else if (inputNames.isNotEmpty()) {
                val audioKey = inputNames.first()
                inputs[audioKey] = inputAudioTensor
            }

            val results = currentSession.run(inputs)

            if (results.size() > 0) {
                val outputAudioTensor = results[0].value
                extractAudioOutput(outputAudioTensor, denoisedFloat, frameSize)
                inferenceSuccess = true

                if (results.size() > 1) {
                    val outputHiddenTensor = results[1].value
                    extractHiddenOutput(outputHiddenTensor, hiddenState)
                }
            }

            inputAudioTensor.close()
            hiddenInTensor.close()
            results.close()
        } catch (e: Exception) {
            Log.w("NeuralNoiseSuppressor", "Inference error during audio frame processing.", e)
            inferenceSuccess = false
        }

        // Fallback to pass-through on inference failure
        if (!inferenceSuccess) {
            return
        }

        // 3. Interpolate between raw and denoised audio according to suppressionLevel (0.0 to 1.0)
        val alpha = suppressionLevel.coerceIn(0.0f, 1.0f)
        for (i in 0 until frameSize) {
            val raw = normalizedInput[i]
            val denoised = denoisedFloat[i]
            val blended = (1.0f - alpha) * raw + alpha * denoised
            val clamped = blended.coerceIn(-1.0f, 1.0f)

            // 4. Write output back to pcmData in-place
            pcmData[i] = (clamped * 32767.0f).toInt().toShort()
        }
    }

    private fun extractAudioOutput(tensorValue: Any?, target: FloatArray, frameSize: Int) {
        when (tensorValue) {
            is Array<*> -> {
                if (tensorValue.isNotEmpty() && tensorValue[0] is FloatArray) {
                    val audioOut = tensorValue[0] as FloatArray
                    System.arraycopy(audioOut, 0, target, 0, minOf(audioOut.size, frameSize))
                }
            }
            is FloatArray -> {
                System.arraycopy(tensorValue, 0, target, 0, minOf(tensorValue.size, frameSize))
            }
        }
    }

    private fun extractHiddenOutput(tensorValue: Any?, hiddenTarget: FloatArray) {
        when (tensorValue) {
            is Array<*> -> {
                if (tensorValue.isNotEmpty() && tensorValue[0] is FloatArray) {
                    val hiddenOut = tensorValue[0] as FloatArray
                    if (hiddenOut.size == hiddenTarget.size) {
                        System.arraycopy(hiddenOut, 0, hiddenTarget, 0, hiddenTarget.size)
                    }
                }
            }
            is FloatArray -> {
                if (tensorValue.size == hiddenTarget.size) {
                    System.arraycopy(tensorValue, 0, hiddenTarget, 0, hiddenTarget.size)
                }
            }
        }
    }

    /**
     * Cleans up ONNX Runtime sessions and environment resources.
     */
    fun release() {
        if (!isModelLoaded) return
        try {
            session?.close()
        } catch (_: OrtException) {
        } catch (_: Exception) {
        }
        try {
            env?.close()
        } catch (_: OrtException) {
        } catch (_: Exception) {
        }
        session = null
        env = null
        isModelLoaded = false
    }
}