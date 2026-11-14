package com.aether.call.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton object managing Call Audio Recording safely on Dispatchers.IO.
 */
object CallRecorderManager {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getRecordingsDir(context: Context): File {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.DIRECTORY_RECORDINGS
        } else {
            "Recordings"
        }
        val dir = File(context.getExternalFilesDir(type), "AetherCallRecordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun startRecording(context: Context, callerNameOrNumber: String) {
        if (_isRecording.value) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        scope.launch {
            val sanitized = callerNameOrNumber.replace("[^a-zA-Z0-9]".toRegex(), "_").take(20).ifBlank { "Unknown" }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Call_${sanitized}_$timeStamp.m4a"
            val outputFile = File(getRecordingsDir(context), fileName)
            currentOutputFile = outputFile

            var recorder: MediaRecorder? = null
            var success = false

            // Attempt 1: AudioSource.VOICE_COMMUNICATION
            try {
                recorder = createMediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }
                success = true
            } catch (_: Exception) {
                recorder?.reset()
                recorder?.release()
                recorder = null
            }

            // Attempt 2 Fallback: AudioSource.MIC
            if (!success) {
                try {
                    recorder = createMediaRecorder(context).apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128000)
                        setAudioSamplingRate(44100)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                    }
                    success = true
                } catch (e: Exception) {
                    e.printStackTrace()
                    recorder?.reset()
                    recorder?.release()
                    recorder = null
                    currentOutputFile = null
                }
            }

            if (success && recorder != null) {
                mediaRecorder = recorder
                _isRecording.value = true
                _recordingDurationSeconds.value = 0

                tickerJob?.cancel()
                tickerJob = launch {
                    while (isActive && _isRecording.value) {
                        delay(1000L)
                        _recordingDurationSeconds.value += 1
                    }
                }
            }
        }
    }

    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext null

        tickerJob?.cancel()
        tickerJob = null
        _isRecording.value = false
        _recordingDurationSeconds.value = 0

        val recorder = mediaRecorder
        mediaRecorder = null

        val file = currentOutputFile
        currentOutputFile = null

        try {
            recorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }

        return@withContext file
    }

    fun getRecordedFiles(context: Context): List<File> {
        val dir = getRecordingsDir(context)
        return dir.listFiles { _, name -> name.endsWith(".m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    private fun createMediaRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
