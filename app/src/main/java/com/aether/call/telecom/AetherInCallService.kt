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
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
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

    fun answer() {
        _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
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

    /**
     * Call this inside your Call.Callback.onDetailsChanged() and when attaching a new call.
     */
    fun syncVideoCallState(call: Call?) {
        if (call == null) {
            _isVideoCall.value = false
            _incomingVideoUpgradeRequest.value = false
            return
        }
        val vState = call.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
        val activeVideo = VideoProfile.isVideo(vState)
        _isVideoCall.value = activeVideo

        try {
            call.videoCall?.registerCallback(videoCallCallback)
            if (activeVideo) {
                val camId = if (_isFrontCamera.value) "1" else "0"
                call.videoCall?.setCamera(camId)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Toggles between Carrier HD Video (STATE_BIDIRECTIONAL) and Voice-Only (STATE_AUDIO_ONLY).
     */
    fun toggleVideoCall() {
        val call = _currentCall.value ?: return
        val videoCall = call.videoCall
        val currentlyVideo = _isVideoCall.value

        if (!currentlyVideo) {
            _isVideoCall.value = true
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

    /**
     * Accepts or declines an incoming request from the other caller to switch to video.
     */
    fun respondToVideoUpgradeRequest(accept: Boolean) {
        val call = _currentCall.value ?: return
        _incomingVideoUpgradeRequest.value = false
        val targetState = if (accept) {
            _isVideoCall.value = true
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

    /**
     * Flips between Front ("1") and Back ("0") cameras during a carrier video call.
     */
    fun flipVideoCamera() {
        val nextFront = !_isFrontCamera.value
        _isFrontCamera.value = nextFront
        val camId = if (nextFront) "1" else "0"
        try {
            _currentCall.value?.videoCall?.setCamera(camId)
        } catch (_: Exception) {
        }
    }

    /**
     * Binds the remote caller's video Surface to the Telecom VideoCall provider.
     */
    fun bindRemoteDisplaySurface(surface: android.view.Surface?) {
        try {
            _currentCall.value?.videoCall?.setDisplaySurface(surface)
        } catch (_: Exception) {
        }
    }

    /**
     * Binds the local self-view PiP Surface to the Telecom VideoCall provider.
     */
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
        } catch (_: Exception) {
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
        IncomingCallOverlayService.stopOverlay(this)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        CallNotificationManager.cancelNotification(this)
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
                CallNotificationManager.showIncomingCallNotification(this@AetherInCallService, call, resolved)
            } else if (currentState == Call.STATE_DIALING || currentState == Call.STATE_CONNECTING ||
                currentState == Call.STATE_ACTIVE || currentState == Call.STATE_HOLDING
            ) {
                CallNotificationManager.showOngoingCallNotification(this@AetherInCallService, call, resolved)
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            CallManager.syncVideoCallState(call)
            @Suppress("DEPRECATION")
            if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
                updateAndResolveCaller(call)
            }
        }

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            CallManager.updateState(state)
            val number = call.details?.handle?.schemeSpecificPart.orEmpty()
            val resolved = CallerIdResolver.resolveCaller(applicationContext, number)

            when (state) {
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
                    handleIncomingRinging(call, number)
                }
                Call.STATE_ACTIVE, Call.STATE_HOLDING -> {
                    val wasJustAnswered = !wasAnswered
                    wasAnswered = true
                    autoAnswerJob?.cancel()
                    autoAnswerJob = null
                    stopTts()
                    flipDetector?.stopListening()
                    flipDetector = null
                    IncomingCallOverlayService.stopOverlay(this@AetherInCallService)

                    CallNotificationManager.cancelIncomingCallNotification(this@AetherInCallService)

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
                    forceClearAllCallUi()
                    CallBridge.stopRecordingIfActive()

                    if (state == Call.STATE_DISCONNECTED) {
                        val disconnectCode = call.details?.disconnectCause?.code
                        if (wasIncomingRinging && !wasAnswered &&
                            (disconnectCode == DisconnectCause.MISSED ||
                                disconnectCode == DisconnectCause.REMOTE)
                        ) {
                            CallNotificationManager.showMissedCallNotification(this@AetherInCallService, resolved)
                            serviceScope.launch(Dispatchers.IO) {
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
                        CallManager.updateCall(null)
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
        CallManager.instance = this
        CallManager.updateCall(call)
        CallManager.syncVideoCallState(call)
        call.registerCallback(callCallback)

        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val initialResolved = CallerIdResolver.resolveCaller(applicationContext, number)
        CallBridge.updateCallerIdentity(
            name = initialResolved.displayName,
            number = number,
            photoUri = initialResolved.photoUri,
        )

        val canSendText = call.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true
        CallManager.canRespondViaText.value = canSendText

        @Suppress("DEPRECATION")
        val state = call.state
        if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
            wasIncomingRinging = false
            wasAnswered = true
            val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(this, call, initialResolved)
            promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)
            launchFullScreenInCall(call, number)
        } else if (state == Call.STATE_RINGING) {
            wasIncomingRinging = true
            wasAnswered = false
            serviceScope.launch {
                val settings = AppSettingsManager(this@AetherInCallService).settingsFlow.first()
                val normIncoming = PhoneNormalizer.normalizeDigits(number)
                val isExplicitlyBlocked = settings.blockedNumbers.any {
                    PhoneNormalizer.normalizeDigits(it) == normIncoming && normIncoming.isNotEmpty()
                }
                val isUnknownBlocked = settings.blockUnknownNumbers && !initialResolved.isSavedContact

                if (isExplicitlyBlocked || isUnknownBlocked) {
                    wasIncomingRinging = false
                    call.reject(false, null)
                } else {
                    handleIncomingRinging(call, number)
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (CallRecordingManager.isRecording.value) {
            CallRecordingManager.stopRecording(applicationContext)
        }
        forceClearAllCallUi()
        if (CallManager.currentCall.value == call) {
            CallManager.updateCall(null)
        }
        if (CallManager.instance == this) {
            CallManager.instance = null
        }
    }

    fun onCallAnsweredByUser() {
        wasAnswered = true
        resolveJob?.cancel()
        // 1. Cancel incoming heads-up notification (1001)
        CallNotificationManager.cancelIncomingCallNotification(this)

        // 2. Immediately switch foreground service to Ongoing Notification (1002)
        val activeCall = CallManager.currentCall.value
        val number = activeCall?.details?.handle?.schemeSpecificPart
            ?: CallBridge.callerNumber.value
        val resolved = CallerIdResolver.resolveCaller(this, number)
        val ongoingNotif = CallNotificationManager.buildOngoingCallNotification(this, activeCall, resolved)
        promoteToForeground(CallNotificationManager.ONGOING_NOTIFICATION_ID, ongoingNotif)

        // 3. Bring InCallActivity to the front
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
                    val resolved = CallerIdResolver.resolveCaller(this, num)
                    CallNotificationManager.showOngoingCallNotification(this, activeCall, resolved)
                }
            }
        }
    }

    private fun launchFullScreenInCall(call: Call?, number: String) {
        IncomingCallOverlayService.stopOverlay(this)
        @Suppress("DEPRECATION")
        val direction = if (call?.state == Call.STATE_RINGING) "INCOMING" else "ACTIVE"
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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
        } catch (_: Exception) {
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIncomingRinging(call: Call, number: String) {
        flipDetector?.stopListening()
        autoAnswerJob?.cancel()
        IncomingCallOverlayService.stopOverlay(this)
        val resolved = CallerIdResolver.resolveCaller(this, number)

        // Post native CallStyle incoming notification (renders as Samsung One UI heads-up pop-up when unlocked
        // and full-screen InCallActivity when locked — with 100% foreground launch privilege on Answer!)
        val incomingNotif = CallNotificationManager.buildIncomingCallNotification(
            context = this,
            caller = resolved,
            overlayIsShowing = false,
        )
        promoteToForeground(CallNotificationManager.INCOMING_NOTIFICATION_ID, incomingNotif)

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        if (isLocked) {
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