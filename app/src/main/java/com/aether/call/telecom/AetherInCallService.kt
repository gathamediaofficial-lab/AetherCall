package com.aether.call.telecom

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import com.aether.call.data.AppSettingsManager
import com.aether.call.ui.InCallActivity
import com.aether.call.util.PhoneNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ICS_TAG = "AetherICS"

object CallManager {

    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState.asStateFlow()

    var instance: AetherInCallService? = null
    var activeInCallService: AetherInCallService?
        get() = instance
        set(value) {
            instance = value
        }

    val isMuted = MutableStateFlow(false)
    val isSpeaker = MutableStateFlow(false)
    val isOnHold = MutableStateFlow(false)
    val canRespondViaText = MutableStateFlow(false)
    val callDurationSeconds = MutableStateFlow(0L)

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    fun answer(asVideo: Boolean = isVideoCall.value) {
        val call = _currentCall.value ?: return
        val incomingVideoState = call.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
        val targetVideoState = if (asVideo && VideoProfile.isVideo(incomingVideoState)) {
            VideoProfile.STATE_BIDIRECTIONAL
        } else {
            VideoProfile.STATE_AUDIO_ONLY
        }
        call.answer(targetVideoState)
    }

    fun answerAsAudioOnly() {
        _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
        _isVideoCall.value = false
    }

    fun hangup() {
        try {
            _currentCall.value?.disconnect()
        } catch (_: Exception) {
        }
        instance?.setMuted(false)
        stopTimer()
        instance?.forceClearAllCallUi()
    }

    fun toggleMute() {
        val newMute = !isMuted.value
        instance?.setMuted(newMute)
        isMuted.value = newMute
    }

    fun toggleMute(muted: Boolean) {
        instance?.setMuted(muted)
        isMuted.value = muted
    }

    fun toggleSpeaker() {
        @Suppress("DEPRECATION")
        val newRoute = if (isSpeaker.value) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        @Suppress("DEPRECATION")
        instance?.setAudioRoute(newRoute)
        isSpeaker.value = (newRoute == CallAudioState.ROUTE_SPEAKER)
    }

    fun setSpeakerphone(enabled: Boolean) {
        @Suppress("DEPRECATION")
        val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        @Suppress("DEPRECATION")
        instance?.setAudioRoute(route)
        isSpeaker.value = enabled
    }

    fun toggleHold() {
        val currentCallObj = _currentCall.value ?: return
        if (isOnHold.value) {
            currentCallObj.unhold()
        } else {
            currentCallObj.hold()
        }
        isOnHold.value = !isOnHold.value
    }

    val isRecording: StateFlow<Boolean> = CallRecordingManager.isRecording

    fun toggleRecording(context: Context) {
        val call = _currentCall.value
        val number = call?.details?.handle?.schemeSpecificPart.orEmpty()
        val name = call?.details?.callerDisplayName.orEmpty().ifBlank { number }
        CallRecordingManager.toggleRecording(
            context = context,
            callerName = name,
            callerNumber = number,
        )
    }

    fun transferLastRecording(context: Context) {
        CallRecordingManager.shareLastRecording(context)
    }

    // --- CARRIER VIDEO CALL (ViLTE / IMS) STATE & CONTROLS ---
    private val _isVideoCall = MutableStateFlow(false)
    val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

    private var pendingOutgoingVideoCall = false

    fun prepareOutgoingVideoCall() {
        pendingOutgoingVideoCall = true
        _isVideoCall.value = true
        _isFrontCamera.value = true
    }

    private val _incomingVideoUpgradeRequest = MutableStateFlow(false)
    val incomingVideoUpgradeRequest: StateFlow<Boolean> = _incomingVideoUpgradeRequest.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _peerVideoDimensions = MutableStateFlow(720 to 1280)
    val peerVideoDimensions: StateFlow<Pair<Int, Int>> = _peerVideoDimensions.asStateFlow()

    private val videoCallCallback = object : InCallService.VideoCall.Callback() {
        override fun onSessionModifyRequestReceived(videoProfile: VideoProfile?) {
            val requestedState = videoProfile?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
            if (VideoProfile.isVideo(requestedState)) {
                _incomingVideoUpgradeRequest.value = true
            } else {
                _isVideoCall.value = false
            }
        }

        override fun onSessionModifyResponseReceived(
            status: Int,
            requestedProfile: VideoProfile?,
            responseProfile: VideoProfile?,
        ) {
            if (status == android.telecom.Connection.VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS) {
                val newState = responseProfile?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
                _isVideoCall.value = VideoProfile.isVideo(newState)
            }
        }

        override fun onCallSessionEvent(event: Int) {}

        override fun onPeerDimensionsChanged(width: Int, height: Int) {
            if (width > 0 && height > 0) {
                _peerVideoDimensions.value = width to height
            }
        }

        override fun onVideoQualityChanged(videoQuality: Int) {}
        override fun onCallDataUsageChanged(dataUsage: Long) {}
        override fun onCameraCapabilitiesChanged(
            cameraCapabilities: VideoProfile.CameraCapabilities?,
        ) {}
    }

    fun syncVideoCallState(call: Call?) {
        if (call == null) {
            pendingOutgoingVideoCall = false
            _isVideoCall.value = false
            _incomingVideoUpgradeRequest.value = false
            return
        }

        val vState = call.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
        @Suppress("DEPRECATION")
        val cState = call.state

        val activeVideo = when {
            VideoProfile.isVideo(vState) -> {
                pendingOutgoingVideoCall = false
                true
            }
            pendingOutgoingVideoCall && (
                    cState == Call.STATE_DIALING ||
                            cState == Call.STATE_CONNECTING ||
                            cState == Call.STATE_SELECT_PHONE_ACCOUNT ||
                            cState == Call.STATE_NEW
                    ) -> {
                true
            }
            else -> false
        }

        _isVideoCall.value = activeVideo
        if (activeVideo && !isSpeaker.value) {
            setSpeakerphone(true)
        }

        try {
            call.videoCall?.registerCallback(videoCallCallback)
            if (activeVideo) {
                val camId = if (_isFrontCamera.value) "1" else "0"
                call.videoCall?.setCamera(camId)
                if (!VideoProfile.isVideo(vState) && cState == Call.STATE_ACTIVE && pendingOutgoingVideoCall) {
                    pendingOutgoingVideoCall = false
                    call.videoCall?.sendSessionModifyRequest(
                        VideoProfile(VideoProfile.STATE_BIDIRECTIONAL, VideoProfile.QUALITY_HIGH),
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    fun toggleVideoCall() {
        val call = _currentCall.value ?: return
        val videoCall = call.videoCall
        val currentlyVideo = _isVideoCall.value

        if (!currentlyVideo) {
            _isVideoCall.value = true
            setSpeakerphone(true)
            try {
                val camId = if (_isFrontCamera.value) "1" else "0"
                videoCall?.setCamera(camId)
                videoCall?.sendSessionModifyRequest(
                    VideoProfile(
                        VideoProfile.STATE_BIDIRECTIONAL,
                        VideoProfile.QUALITY_HIGH,
                    ),
                )
            } catch (_: Exception) {
            }
        } else {
            _isVideoCall.value = false
            try {
                videoCall?.setCamera(null)
                videoCall?.setPreviewSurface(null)
                videoCall?.setDisplaySurface(null)
                videoCall?.sendSessionModifyRequest(
                    VideoProfile(VideoProfile.STATE_AUDIO_ONLY),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun respondToVideoUpgradeRequest(accept: Boolean) {
        val call = _currentCall.value ?: return
        _incomingVideoUpgradeRequest.value = false
        val targetState = if (accept) {
            _isVideoCall.value = true
            setSpeakerphone(true)
            VideoProfile.STATE_BIDIRECTIONAL
        } else {
            VideoProfile.STATE_AUDIO_ONLY
        }
        try {
            if (accept) {
                call.videoCall?.setCamera(if (_isFrontCamera.value) "1" else "0")
            }
            call.videoCall?.sendSessionModifyResponse(VideoProfile(targetState))
        } catch (_: Exception) {
        }
    }

    fun flipVideoCamera() {
        val nextFront = !_isFrontCamera.value
        _isFrontCamera.value = nextFront
        val camId = if (nextFront) "1" else "0"
        try {
            _currentCall.value?.videoCall?.setCamera(camId)
        } catch (_: Exception) {
        }
    }

    fun bindRemoteDisplaySurface(surface: android.view.Surface?) {
        try {
            _currentCall.value?.videoCall?.setDisplaySurface(surface)
        } catch (_: Exception) {
        }
    }

    fun bindLocalPreviewSurface(surface: android.view.Surface?) {
        try {
            val videoCall = _currentCall.value?.videoCall ?: return
            if (surface != null) {
                val camId = if (_isFrontCamera.value) "1" else "0"
                videoCall.setCamera(camId)
            }
            videoCall.setPreviewSurface(surface)
        } catch (_: Exception) {
        }
    }

    fun playDtmf(digit: Char) {
        _currentCall.value?.let { call ->
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        }
    }

    @Suppress("DEPRECATION")
    fun selectSimForWaitingCall(context: Context, slotIndex: Int) {
        val call = _currentCall.value ?: return
        val handle = com.aether.call.util.SimAccountResolver.findPhoneAccountHandleForSlot(context, slotIndex)
        if (handle != null) {
            try {
                call.phoneAccountSelected(handle, false)
                val simInfo = com.aether.call.util.SimAccountResolver.resolveFromPhoneAccountHandle(context, handle)
                CallBridge.simLabel.value = simInfo.displayLabel
            } catch (e: Exception) {
                Log.e(ICS_TAG, "selectSimForWaitingCall failed", e)
            }
        }
    }

    internal fun updateCall(call: Call?) {
        _currentCall.value = call
        @Suppress("DEPRECATION")
        val state = call?.state ?: Call.STATE_DISCONNECTED
        updateState(state)
    }

    internal fun updateState(state: Int) {
        _callState.value = state
        if (state == Call.STATE_ACTIVE) {
            startTimer()
        } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            stopTimer()
            resetStates()
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        callDurationSeconds.value = 0L
        timerJob = managerScope.launch {
            while (isActive) {
                delay(1000)
                callDurationSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun resetStates() {
        pendingOutgoingVideoCall = false
        _isVideoCall.value = false
        isMuted.value = false
        isSpeaker.value = false
        isOnHold.value = false
        callDurationSeconds.value = 0L
    }
}

class AetherInCallService : InCallService() {

    private var flipDetector: FlipToSilenceDetector? = null
    private var ttsEngine: TextToSpeech? = null
    private var autoAnswerJob: Job? = null
    private var resolveJob: Job? = null
    private var wasIncomingRinging = false
    private var wasAnswered = false
    @Volatile private var incomingOverlayShowing = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun stopTts() {
        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        } catch (_: Exception) {
        }
        ttsEngine = null
    }

    internal fun promoteToForeground(notificationId: Int, notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                )
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(ICS_TAG, "startForeground failed", e)
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                nm?.notify(notificationId, notification)
            } catch (_: Exception) {
            }
        }
    }

    fun forceClearAllCallUi() {
        resolveJob?.cancel()
        resolveJob = null
        autoAnswerJob?.cancel()
        autoAnswerJob = null
        stopTts()
        flipDetector?.stopListening()
        flipDetector = null
        incomingOverlayShowing = false
        IncomingCallOverlayService.stopOverlay(this)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        CallNotificationManager.cancelNotification(this)
    }

    /**
     * Handles Dual-SIM "Ask every time" (Call.STATE_SELECT_PHONE_ACCOUNT) so Samsung's native
     * SIM picker never takes over outgoing voice or video calls.
     */
    @Suppress("MissingPermission", "DEPRECATION")
    private fun handleSelectPhoneAccount(call: Call) {
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultHandle = tm?.getDefaultOutgoingPhoneAccount("tel")
            val capableAccounts = tm?.callCapablePhoneAccounts.orEmpty()

            if (defaultHandle != null) {
                Log.d(ICS_TAG, "Auto-selecting default PhoneAccountHandle=$defaultHandle for STATE_SELECT_PHONE_ACCOUNT")
                call.phoneAccountSelected(defaultHandle, false)
            } else if (capableAccounts.size == 1) {
                val singleHandle = capableAccounts.first()
                Log.d(ICS_TAG, "Auto-selecting single PhoneAccountHandle=$singleHandle for STATE_SELECT_PHONE_ACCOUNT")
                call.phoneAccountSelected(singleHandle, false)
            } else {
                Log.d(ICS_TAG, "Device set to 'Ask every time' with ${capableAccounts.size} SIMs — awaiting user selection in InCallActivity")
            }
        } catch (e: Exception) {
            Log.e(ICS_TAG, "handleSelectPhoneAccount failed", e)
        }
    }

    private fun updateAndResolveCaller(call: Call) {
        val rawNumber = call.details?.handle?.schemeSpecificPart?.trim().orEmpty()
        if (rawNumber.isNotBlank()) {
            CallBridge.callerNumber.value = rawNumber
            if (CallBridge.callerName.value == "Unknown Caller" || CallBridge.callerName.value.isBlank()) {
                CallBridge.callerName.value = rawNumber
            }
        }
        resolveJob?.cancel()
        resolveJob = serviceScope.launch(Dispatchers.IO) {
            val resolved = CallerIdResolver.resolveCaller(applicationContext, rawNumber)
            CallBridge.updateCallerIdentity(
                name = resolved.displayName,
                number = rawNumber,
                photoUri = resolved.photoUri,
            )
            @Suppress("DEPRECATION")
            val currentState = call.state
            if (CallManager.currentCall.value == null ||
                currentState == Call.STATE_DISCONNECTED ||
                currentState == Call.STATE_DISCONNECTING
            ) {
                CallNotificationManager.cancelNotification(this@AetherInCallService)
                return@launch
            }
            if (currentState == Call.STATE_RINGING && !wasAnswered) {
                CallNotificationManager.showIncomingCallNotification(
                    this@AetherInCallService,
                    call,
                    resolved,
                    overlayIsShowing = incomingOverlayShowing,
                )
            } else if (
                currentState == Call.STATE_SELECT_PHONE_ACCOUNT ||
                currentState == Call.STATE_DIALING ||
                currentState == Call.STATE_CONNECTING ||
                currentState == Call.STATE_ACTIVE ||
                currentState == Call.STATE_HOLDING
            ) {
                CallNotificationManager.showOngoingCallNotification(this@AetherInCallService, call, resolved)
            }
        }
    }

    private fun getFastResolvedCaller(number: String): ResolvedCaller {
        val name = CallBridge.callerName.value.ifBlank { number.ifBlank { "Unknown Caller" } }
        val photoUri = CallBridge.callerPhotoUri.value
        return ResolvedCaller(
            rawNumber = number,
            displayName = name,
            photoUri = photoUri,
            isSavedContact = name != number && name != "Unknown Caller",
            label = "Mobile",
        )
    }

    private val callCallback = object : Call.Callback() {
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            val simInfo = com.aether.call.util.SimAccountResolver.resolveSimForActiveCall(this@AetherInCallService, call)
            CallBridge.simLabel.value = simInfo.displayLabel
            CallManager.syncVideoCallState(call)
            @Suppress("DEPRECATION")
            if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
                updateAndResolveCaller(call)
            }
        }

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(ICS_TAG, "onStateChanged state=$state")
            CallManager.updateState(state)
            val number = call.details?.handle?.schemeSpecificPart.orEmpty()
            val resolved = getFastResolvedCaller(number)

            when (state) {
                Call.STATE_SELECT_PHONE_ACCOUNT -> {
                    handleSelectPhoneAccount(call)
                    val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(
                        this@AetherInCallService,
                        call,
                        resolved,
                    )
                    promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
                    launchFullScreenInCall(call, number)
                }
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(
                        this@AetherInCallService,
                        call,
                        resolved,
                    )
                    promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
                    launchFullScreenInCall(call, number)
                }
                Call.STATE_RINGING -> {
                    wasIncomingRinging = true
                    wasAnswered = false
                    handleIncomingRinging(call, number, resolved)
                }
                Call.STATE_ACTIVE, Call.STATE_HOLDING -> {
                    val wasJustAnswered = !wasAnswered
                    wasAnswered = true
                    autoAnswerJob?.cancel()
                    autoAnswerJob = null
                    stopTts()
                    flipDetector?.stopListening()
                    flipDetector = null
                    incomingOverlayShowing = false
                    IncomingCallOverlayService.stopOverlay(this@AetherInCallService)

                    val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(
                        this@AetherInCallService,
                        call,
                        resolved,
                    )
                    promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)

                    if (wasJustAnswered) {
                        launchFullScreenInCall(call, number)
                    }

                    CallBridge.checkAutoRecordAndStart(this@AetherInCallService)
                    serviceScope.launch {
                        val settings = AppSettingsManager(this@AetherInCallService).settingsFlow.first()
                        if (settings.vibrateOnAnswer) {
                            triggerShortVibration()
                        }
                        if (settings.playConnectTone) {
                            try {
                                ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
                                    .startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> {
                    if (state == Call.STATE_DISCONNECTED) {
                        CallManager.updateCall(null)
                        forceClearAllCallUi()
                        CallBridge.stopRecordingIfActive()

                        val disconnectCode = call.details?.disconnectCause?.code
                        if (wasIncomingRinging && !wasAnswered &&
                            (disconnectCode == DisconnectCause.MISSED ||
                                    disconnectCode == DisconnectCause.REMOTE)
                        ) {
                            serviceScope.launch(Dispatchers.IO) {
                                val fullResolved = CallerIdResolver.resolveCaller(applicationContext, number)
                                CallNotificationManager.showMissedCallNotification(this@AetherInCallService, fullResolved)
                                delay(450L)
                                CallNotificationManager.suppressNativeSamsungMissedCallNotification(this@AetherInCallService)
                            }
                        }
                        wasIncomingRinging = false
                        wasAnswered = false

                        serviceScope.launch {
                            val settings = AppSettingsManager(this@AetherInCallService).settingsFlow.first()
                            if (settings.vibrateOnEnd) {
                                triggerShortVibration()
                            }
                            if (settings.playEndTone) {
                                try {
                                    ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
                                        .startTone(ToneGenerator.TONE_PROP_PROMPT, 220)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.createNotificationChannel(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        @Suppress("DEPRECATION")
        val state = call.state
        Log.d(ICS_TAG, "onCallAdded state=$state hasAccount=${call.details?.accountHandle != null}")
        CallManager.instance = this
        CallManager.updateCall(call)
        val simInfo = com.aether.call.util.SimAccountResolver.resolveSimForActiveCall(this, call)
        CallBridge.simLabel.value = simInfo.displayLabel
        CallManager.syncVideoCallState(call)
        call.registerCallback(callCallback)

        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val initialResolved = getFastResolvedCaller(number)

        val canSendText = call.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true
        CallManager.canRespondViaText.value = canSendText

        when (state) {
            Call.STATE_SELECT_PHONE_ACCOUNT -> {
                wasIncomingRinging = false
                wasAnswered = true
                handleSelectPhoneAccount(call)
                val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(this, call, initialResolved)
                promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
                launchFullScreenInCall(call, number)
                updateAndResolveCaller(call)
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                wasIncomingRinging = false
                wasAnswered = true
                val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(this, call, initialResolved)
                promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
                launchFullScreenInCall(call, number)
                updateAndResolveCaller(call)
            }
            Call.STATE_RINGING -> {
                wasIncomingRinging = true
                wasAnswered = false
                // 1. Immediately claim foreground notification & UI synchronously (< 5ms)
                handleIncomingRinging(call, number, initialResolved)

                // 2. Resolve contact on IO thread FIRST, then check blocklist so saved contacts are never rejected!
                serviceScope.launch(Dispatchers.IO) {
                    val fullResolved = CallerIdResolver.resolveCaller(applicationContext, number)
                    val settings = AppSettingsManager(this@AetherInCallService).settingsFlow.first()
                    val normIncoming = PhoneNormalizer.normalizeDigits(number)
                    val isExplicitlyBlocked = settings.blockedNumbers.any {
                        PhoneNormalizer.normalizeDigits(it) == normIncoming && normIncoming.isNotEmpty()
                    }
                    val isUnknownBlocked = settings.blockUnknownNumbers && !fullResolved.isSavedContact

                    if (isExplicitlyBlocked || isUnknownBlocked) {
                        Log.d(ICS_TAG, "Rejecting blocked/unknown call from $number")
                        wasIncomingRinging = false
                        call.reject(false, null)
                        withContext(Dispatchers.Main) {
                            forceClearAllCallUi()
                        }
                    } else {
                        CallBridge.updateCallerIdentity(
                            name = fullResolved.displayName,
                            number = number,
                            photoUri = fullResolved.photoUri,
                        )
                        @Suppress("DEPRECATION")
                        if (call.state == Call.STATE_RINGING && !wasAnswered) {
                            CallNotificationManager.showIncomingCallNotification(
                                this@AetherInCallService,
                                call,
                                fullResolved,
                                overlayIsShowing = incomingOverlayShowing,
                            )
                        }
                    }
                }
            }
            else -> {
                val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(this, call, initialResolved)
                promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
                launchFullScreenInCall(call, number)
                updateAndResolveCaller(call)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (CallRecordingManager.isRecording.value) {
            CallRecordingManager.stopRecording(applicationContext)
        }
        if (calls.isEmpty()) {
            if (CallManager.currentCall.value == call) {
                CallManager.updateCall(null)
            }
            forceClearAllCallUi()
            if (CallManager.instance == this) {
                CallManager.instance = null
            }
        }
    }

    fun onCallAnsweredByUser() {
        wasAnswered = true
        resolveJob?.cancel()
        incomingOverlayShowing = false
        IncomingCallOverlayService.stopOverlay(this)

        val activeCall = CallManager.currentCall.value
        val number = activeCall?.details?.handle?.schemeSpecificPart ?: CallBridge.callerNumber.value
        val resolved = getFastResolvedCaller(number)
        val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(this, activeCall, resolved)
        promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
        bringInCallUiToForeground()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        bringInCallUiToForeground()
    }

    fun bringInCallUiToForeground() {
        val activeCall = CallManager.currentCall.value
        val number = activeCall?.details?.handle?.schemeSpecificPart
            ?: CallBridge.callerNumber.value
        launchFullScreenInCall(activeCall, number)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState?.let {
            CallManager.isMuted.value = it.isMuted
            CallManager.isSpeaker.value = (it.route == CallAudioState.ROUTE_SPEAKER)
            CallManager.currentCall.value?.let { activeCall ->
                @Suppress("DEPRECATION")
                if (activeCall.state == Call.STATE_ACTIVE || activeCall.state == Call.STATE_HOLDING) {
                    val num = activeCall.details?.handle?.schemeSpecificPart.orEmpty()
                    val resolved = getFastResolvedCaller(num)
                    CallNotificationManager.showOngoingCallNotification(this, activeCall, resolved)
                }
            }
        }
    }

    private fun launchFullScreenInCall(call: Call?, number: String) {
        Log.d(ICS_TAG, "launchFullScreenInCall requested")
        incomingOverlayShowing = false
        IncomingCallOverlayService.stopOverlay(this)
        @Suppress("DEPRECATION")
        val direction = if (call?.state == Call.STATE_RINGING) "INCOMING" else "ACTIVE"
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("CALL_NUMBER", number)
            putExtra("CALLER_NAME", CallBridge.callerName.value)
            putExtra("CALL_DIRECTION", direction)
        }

        try {
            val pi = PendingIntent.getActivity(
                this,
                999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                pi.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                pi.send()
            }
        } catch (e: Exception) {
            Log.e(ICS_TAG, "PendingIntent launch failed", e)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(ICS_TAG, "startActivity failed", e)
        }
    }

    private fun handleIncomingRinging(
        call: Call,
        number: String,
        resolved: ResolvedCaller = getFastResolvedCaller(number),
    ) {
        flipDetector?.stopListening()
        autoAnswerJob?.cancel()
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val canDrawOverlay = Settings.canDrawOverlays(this)
        val isVideo = CallManager.isVideoCall.value || VideoProfile.isVideo(call.details?.videoState ?: 0)

        val useOwnPopup = !isLocked && canDrawOverlay && !isVideo
        Log.d(ICS_TAG, "handleIncomingRinging locked=$isLocked canDrawOverlay=$canDrawOverlay isVideo=$isVideo ownPopup=$useOwnPopup")

        if (useOwnPopup) {
            incomingOverlayShowing = true
            IncomingCallOverlayService.startOverlay(this, number)
        } else {
            incomingOverlayShowing = false
            IncomingCallOverlayService.stopOverlay(this)
        }

        val incomingNotif = CallNotificationManager.buildIncomingCallNotification(
            context = this,
            caller = resolved,
            overlayIsShowing = incomingOverlayShowing,
        )
        promoteToForeground(CallNotificationManager.INCOMING_NOTIFICATION_ID, incomingNotif)

        if (!useOwnPopup) {
            launchFullScreenInCall(call, number)
        }

        serviceScope.launch {
            val settings = AppSettingsManager(this@AetherInCallService).settingsFlow.first()
            if (settings.autoAnswerEnabled) {
                autoAnswerJob = serviceScope.launch {
                    delay(settings.autoAnswerDelaySeconds * 1000L)
                    @Suppress("DEPRECATION")
                    if (call.state == Call.STATE_RINGING) {
                        CallBridge.answerCall()
                    }
                }
            }
            if (settings.flipToSilence) {
                flipDetector = FlipToSilenceDetector(this@AetherInCallService) {
                    silenceActiveCallRinger()
                }
                flipDetector?.startListening()
            }
            if (settings.readCallerNameAloud) {
                stopTts()
                ttsEngine = TextToSpeech(applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        @Suppress("DEPRECATION")
                        ttsEngine?.speak("Incoming call from ${resolved.displayName}", TextToSpeech.QUEUE_FLUSH, null)
                    }
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun triggerShortVibration() {
        try {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("MissingPermission")
    private fun silenceActiveCallRinger() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    telecomManager?.silenceRinger()
                } catch (_: SecurityException) {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    @Suppress("DEPRECATION")
                    audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
            } else {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                @Suppress("DEPRECATION")
                audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
            triggerShortVibration()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}