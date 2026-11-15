package com.aether.call.telecom

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.aether.call.util.showToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallRecordingManager {

    private var mediaRecorder: MediaRecorder? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var activeOutputUri: Uri? = null
    private var activeLegacyFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _lastSavedRecordingUri = MutableStateFlow<Uri?>(null)
    val lastSavedRecordingUri: StateFlow<Uri?> = _lastSavedRecordingUri

    private val _lastSavedFileName = MutableStateFlow<String?>(null)
    val lastSavedFileName: StateFlow<String?> = _lastSavedFileName

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
     * Starts recording the active call and writes directly to MediaStore.Audio.Media
     * under Music/Aether Call Recordings so it appears in File Manager -> Audio / Music.
     */
    fun startRecording(
        context: Context,
        callerName: String,
        callerNumber: String,
    ): Boolean {
        if (_isRecording.value) return true

        val cleanName = (callerName.takeIf { it.isNotBlank() } ?: callerNumber.ifBlank { "Unknown" })
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(28)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayFileName = "Call_${cleanName}_$timestamp.m4a"

        // Try audio sources in order of compatibility across Android OEMs
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )

        for (source in audioSources) {
            try {
                val (uri, pfd, legacyFile) = createAudioMediaStoreTarget(
                    context = context,
                    fileName = displayFileName,
                    callerLabel = callerName.ifBlank { callerNumber },
                )

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

                if (pfd != null) {
                    recorder.setOutputFile(pfd.fileDescriptor)
                } else if (legacyFile != null) {
                    recorder.setOutputFile(legacyFile.absolutePath)
                } else {
                    recorder.release()
                    continue
                }

                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                activePfd = pfd
                activeOutputUri = uri
                activeLegacyFile = legacyFile
                _lastSavedFileName.value = displayFileName
                _isRecording.value = true

                context.showToast("Recording started: $displayFileName")
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                cleanupFailedAttempt(context)
            }
        }

        context.showToast("Unable to start call recording. Check microphone permission.")
        return false
    }

    /**
     * Stops the active recording with robust error handling and thread isolation.
     */
    fun stopRecording(context: Context): Uri? {
        if (!_isRecording.value && mediaRecorder == null) return _lastSavedRecordingUri.value

        var savedUri: Uri? = activeOutputUri
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

        try {
            activePfd?.close()
        } catch (_: Exception) {
        } finally {
            activePfd = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                }
                context.contentResolver.update(savedUri, values, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val publicMusicFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Aether Call Recordings",
        )
        val scannedFile = activeLegacyFile ?: File(publicMusicFolder, _lastSavedFileName.value.orEmpty())
        if (scannedFile.exists()) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(scannedFile.absolutePath),
                arrayOf("audio/mp4"),
            ) { _, scannedUri ->
                if (scannedUri != null) {
                    _lastSavedRecordingUri.value = scannedUri
                }
            }
            if (savedUri == null) {
                savedUri = Uri.fromFile(scannedFile)
            }
        }

        _isRecording.value = false
        _lastSavedRecordingUri.value = savedUri
        activeOutputUri = null
        activeLegacyFile = null
        return savedUri
    }

    /**
     * Shares or transfers the most recent call recording via Android's system share sheet.
     */
    fun shareLastRecording(context: Context) {
        val uri = _lastSavedRecordingUri.value ?: activeOutputUri
        if (uri == null) {
            context.showToast("No saved call recording found yet. Stop recording first to transfer.")
            return
        }

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
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

    private fun createAudioMediaStoreTarget(
        context: Context,
        fileName: String,
        callerLabel: String,
    ): Triple<Uri?, ParcelFileDescriptor?, File?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".m4a"))
                put(MediaStore.Audio.Media.ARTIST, callerLabel.ifBlank { "Aether Call" })
                put(MediaStore.Audio.Media.ALBUM, "Aether Call Recordings")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                // Crucial: Relative path inside DIRECTORY_MUSIC + IS_MUSIC = 1 ensures File Manager lists it under Audio/Music!
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/Aether Call Recordings",
                )
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues)
            val pfd = itemUri?.let { resolver.openFileDescriptor(it, "w") }
            Triple(itemUri, pfd, null)
        } else {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Aether Call Recordings",
            ).apply {
                if (!exists()) mkdirs()
            }
            val outFile = File(musicDir, fileName)
            Triple(Uri.fromFile(outFile), null, outFile)
        }
    }

    private fun cleanupFailedAttempt(context: Context) {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }

        try {
            activePfd?.close()
        } catch (_: Exception) {
        } finally {
            activePfd = null
        }

        activeOutputUri?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
        }
        activeOutputUri = null

        activeLegacyFile?.let { file ->
            if (file.exists()) file.delete()
        }
        activeLegacyFile = null
        _isRecording.value = false
    }
}