package com.aether.call.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.Call
import android.telecom.VideoProfile
import com.aether.call.data.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Unified CallBridge providing reactive state flows and telecom action triggers for InCallActivity.
 */
object CallBridge {

    private val scope = CoroutineScope(Dispatchers.IO)

    val callState: StateFlow<Int> get() = CallManager.callState
    val isMuted: StateFlow<Boolean> get() = CallManager.isMuted
    val isSpeakerOn: StateFlow<Boolean> get() = CallManager.isSpeaker
    val isHoldOn: StateFlow<Boolean> get() = CallManager.isOnHold
    val isRecording: StateFlow<Boolean> get() = CallRecordingManager.isRecording

    val callerName: MutableStateFlow<String> = MutableStateFlow("Unknown Caller")
    val callerNumber: MutableStateFlow<String> = MutableStateFlow("")
    val callerPhotoUri: MutableStateFlow<String?> = MutableStateFlow(null)
    val simLabel: MutableStateFlow<String> = MutableStateFlow("IND-JIO")

    fun updateCallerIdentity(name: String, number: String, photoUri: String? = null) {
        if (name.isNotBlank()) callerName.value = name
        if (number.isNotBlank()) callerNumber.value = number
        callerPhotoUri.value = photoUri
    }

    fun answerCall(asVideo: Boolean = CallManager.isVideoCall.value) {
        // 1. Immediately answer the Telecom call
        try {
            CallManager.answer(asVideo)
        } catch (_: Exception) {
            CallManager.answer(asVideo)
        }
        // 2. Immediately dismiss the incoming heads-up notification banner and bring UI to front
        CallManager.instance?.onCallAnsweredByUser()
    }

    fun answerCallAsAudioOnly() {
        try {
            CallManager.answerAsAudioOnly()
        } catch (_: Exception) {
            CallManager.answerAsAudioOnly()
        }
        CallManager.instance?.onCallAnsweredByUser()
    }

    fun rejectCall() {
        try {
            CallManager.currentCall.value?.reject(false, null)
        } catch (_: Exception) {
            CallManager.hangup()
        }
    }

    fun disconnectCall() {
        try {
            CallManager.currentCall.value?.disconnect()
        } catch (_: Exception) {
            CallManager.hangup()
        }
    }

    fun toggleMute() = CallManager.toggleMute()
    fun toggleSpeaker() = CallManager.toggleSpeaker()
    fun toggleHold() = CallManager.toggleHold()
    fun playDtmfTone(digit: Char) = CallManager.playDtmf(digit)

    fun toggleRecording(context: Context) {
        CallRecordingManager.toggleRecording(context, callerName.value, callerNumber.value)
    }

    fun checkAutoRecordAndStart(context: Context) {
        scope.launch {
            val settings = AppSettingsManager(context).settingsFlow.first()
            if (settings.autoRecordCalls && !CallRecordingManager.isRecording.value) {
                CallRecordingManager.startRecording(context, callerName.value, callerNumber.value)
            }
        }
    }

    fun stopRecordingIfActive(context: Context? = null) {
        if (CallRecordingManager.isRecording.value) {
            val ctx = context ?: CallManager.instance ?: return
            CallRecordingManager.stopRecording(ctx)
        }
    }

    fun shareLastRecording(context: Context) {
        CallRecordingManager.shareLastRecording(context)
    }

    fun transferLastRecording(context: Context) {
        CallRecordingManager.shareLastRecording(context)
    }

    fun rejectWithSms(context: Context, message: String) {
        val handle = callerNumber.value.ifBlank {
            CallManager.currentCall.value?.details?.handle?.schemeSpecificPart ?: ""
        }
        if (handle.isNotBlank()) {
            try {
                CallManager.currentCall.value?.reject(true, message)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$handle")).apply {
                        putExtra("sms_body", message)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
        CallManager.hangup()
    }
}
