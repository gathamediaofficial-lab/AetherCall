package com.aether.call.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log

/**
 * Singleton managing incoming call ringtone playback and vibration across all Android devices and OEMs.
 */
object CallRingtonePlayer {

    private const val TAG = "CallRingtonePlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    @Volatile
    private var isPlaying = false

    @Synchronized
    fun start(context: Context, customRingtoneUri: String? = null) {
        if (isPlaying) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val ringerMode = audioManager.ringerMode

        Log.d(TAG, "start called with ringerMode=$ringerMode, customUri=$customRingtoneUri")

        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.d(TAG, "Device is in Silent mode; skipping ringtone and vibration")
            return
        }

        isPlaying = true

        // 1. Start Vibration for VIBRATE and NORMAL modes
        try {
            val vibr = getVibrator(context)
            vibrator = vibr
            if (vibr?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibr.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibr.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }

        // 2. Play Ringtone Audio for NORMAL mode
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val uri: Uri = customRingtoneUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_RINGTONE_URI

                Log.d(TAG, "Attempting to play ringtone from URI: $uri")

                var started = false

                // Method A: MediaPlayer with USAGE_NOTIFICATION_RINGTONE
                try {
                    val player = MediaPlayer().apply {
                        setDataSource(context.applicationContext, uri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        start()
                    }
                    mediaPlayer = player
                    started = true
                    Log.d(TAG, "MediaPlayer successfully started ringtone")
                } catch (e: Exception) {
                    Log.w(TAG, "MediaPlayer failed to play ringtone URI ($uri), trying RingtoneManager fallback", e)
                }

                // Method B: RingtoneManager fallback
                if (!started) {
                    val rt = RingtoneManager.getRingtone(context.applicationContext, uri)
                    if (rt != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            rt.isLooping = true
                            rt.audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        }
                        rt.play()
                        ringtone = rt
                        started = true
                        Log.d(TAG, "RingtoneManager successfully started fallback ringtone")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ringtone player", e)
            }
        }
    }

    @Synchronized
    fun stop(context: Context? = null) {
        if (!isPlaying && mediaPlayer == null && ringtone == null && vibrator == null) return
        Log.d(TAG, "stop called")
        isPlaying = false

        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }

        try {
            ringtone?.let { rt ->
                if (rt.isPlaying) {
                    rt.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Ringtone", e)
        } finally {
            ringtone = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        } finally {
            vibrator = null
        }
    }

    @Suppress("MissingPermission")
    @Synchronized
    fun silence(context: Context) {
        Log.d(TAG, "silence called")
        stop(context)
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                telecomManager?.silenceRinger()
            }
        } catch (_: Exception) {
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
