package com.aether.call.telecom

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aether.call.util.showToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust Singleton managing In-Call Audio Recording, MPEG-4 byte stream flushes,
 * and MediaStore audio file publishing.
 */
object CallRecordingManager {

    private var mediaRecorder: MediaRecorder? = null
    private var activeOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _lastSavedRecordingUri = MutableStateFlow<Uri?>(null)
    val lastSavedRecordingUri: StateFlow<Uri?> = _lastSavedRecordingUri

    private val _lastSavedFileName = MutableStateFlow<String?>(null)
    val lastSavedFileName: StateFlow<String?> = _lastSavedFileName

    /**
     * Directory where call recordings are stored.
     */
    fun getRecordingsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Aether Call Recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Returns list of recorded call audio files ordered newest first.
     */
    fun getRecordedFiles(context: Context): List<File> {
        val dir = getRecordingsDir(context)
        return dir.listFiles { _, name -> name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".3gp") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Deletes a recorded call file.
     */
    fun deleteRecording(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Toggles call recording on or off.
     */
    fun toggleRecording(
        context: Context,
        callerName: String,
        callerNumber: String,
    ): Boolean {
        return if (_isRecording.value) {
            stopRecording(context)
            false
        } else {
            startRecording(context, callerName, callerNumber)
        }
    }

    /**
     * Starts recording the active call directly to a local MPEG-4 audio file.
     */
    fun startRecording(
        context: Context,
        callerName: String,
        callerNumber: String,
    ): Boolean {
        if (_isRecording.value) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            context.showToast("Record Audio permission is required to record calls")
            return false
        }

        val cleanName = (callerName.takeIf { it.isNotBlank() } ?: callerNumber.ifBlank { "Unknown" })
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(28)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayFileName = "Call_${cleanName}_$timestamp.m4a"

        val recordingsDir = getRecordingsDir(context)
        val outputFile = File(recordingsDir, displayFileName)

        // Try AudioSources in order of Android OEM in-call compatibility
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT,
        )

        for (source in audioSources) {
            try {
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recorder.setAudioSource(source)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
                recorder.setOutputFile(outputFile.absolutePath)

                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                activeOutputFile = outputFile
                _lastSavedFileName.value = displayFileName
                _isRecording.value = true

                context.showToast("Recording started: $displayFileName")
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupFailedAttempt()
            }
        }

        context.showToast("Unable to start call recording on this device")
        return false
    }

    /**
     * Stops the active recording, verifies non-zero byte size, and publishes to MediaStore.
     */
    fun stopRecording(context: Context): Uri? {
        if (!_isRecording.value && mediaRecorder == null) return _lastSavedRecordingUri.value

        val recordedFile = activeOutputFile
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (stopErr: Exception) {
                    stopErr.printStackTrace()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        _isRecording.value = false

        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0L) {
            val fileUri = Uri.fromFile(recordedFile)

            // Publish to MediaStore so File Manager & Audio Players can discover it
            val mediaStoreUri = publishToMediaStore(context, recordedFile)
            val finalUri = mediaStoreUri ?: fileUri

            _lastSavedRecordingUri.value = finalUri

            // Scan file with MediaScannerConnection
            MediaScannerConnection.scanFile(
                context,
                arrayOf(recordedFile.absolutePath),
                arrayOf("audio/mp4"),
            ) { _, scannedUri ->
                if (scannedUri != null) {
                    _lastSavedRecordingUri.value = scannedUri
                }
            }

            val sizeKb = recordedFile.length() / 1024
            context.showToast("Call recording saved (${sizeKb} KB)")
            activeOutputFile = null
            return finalUri
        } else {
            if (recordedFile != null && recordedFile.exists()) {
                recordedFile.delete()
            }
            context.showToast("Recording failed: no audio captured")
            activeOutputFile = null
            return null
        }
    }

    /**
     * Shares or transfers the most recent call recording via Android's system share sheet.
     */
    fun shareLastRecording(context: Context) {
        val lastFile = getRecordedFiles(context).firstOrNull()
        if (lastFile == null || lastFile.length() == 0L) {
            context.showToast("No saved call recording found yet. Stop recording first to transfer.")
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                lastFile,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/m4a"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Transfer call recording").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            context.showToast("Unable to share recording: ${e.message}")
        }
    }

    private fun publishToMediaStore(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Uri.fromFile(file)
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.TITLE, file.name.removeSuffix(".m4a"))
                put(MediaStore.Audio.Media.ARTIST, "Aether Call")
                put(MediaStore.Audio.Media.ALBUM, "Aether Call Recordings")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Aether Call Recordings")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cleanupFailedAttempt() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }

        activeOutputFile?.let { file ->
            if (file.exists()) file.delete()
        }
        activeOutputFile = null
        _isRecording.value = false
    }
}
