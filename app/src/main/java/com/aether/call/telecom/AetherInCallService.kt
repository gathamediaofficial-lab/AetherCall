package com.aether.call.telecom

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import android.view.Surface
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

    private val _allCalls = MutableStateFlow<List<Call>>(emptyList())
    val allCalls: StateFlow<List<Call>> = _allCalls.asStateFlow()

    private val _isConferenceCall = MutableStateFlow(false)
    val isConferenceCall: StateFlow<Boolean> = _isConferenceCall.asStateFlow()

    private val _canMergeCalls = MutableStateFlow(false)
    val canMergeCalls: StateFlow<Boolean> = _canMergeCalls.asStateFlow()

    private val _canSwapCalls = MutableStateFlow(false)
    val canSwapCalls: StateFlow<Boolean> = _canSwapCalls.asStateFlow()

    private val _secondCallName = MutableStateFlow<String?>(null)
    val secondCallName: StateFlow<String?> = _secondCallName.asStateFlow()

    private val _secondCallNumber = MutableStateFlow<String?>(null)
    val secondCallNumber: StateFlow<String?> = _secondCallNumber.asStateFlow()

    fun syncCallsList(calls: List<Call>) {
        _allCalls.value = calls
        _canMergeCalls.value = calls.size >= 2
        _canSwapCalls.value = calls.any { it.state == Call.STATE_HOLDING } && calls.any { it.state == Call.STATE_ACTIVE }

        val activeConf = calls.firstOrNull {
            it.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
                    it.children.size > 0
        }
        _isConferenceCall.value = activeConf != null

        val heldCall = calls.firstOrNull { it.state == Call.STATE_HOLDING }
        if (heldCall != null) {
            val handle = heldCall.details?.handle?.schemeSpecificPart.orEmpty()
            val name = heldCall.details?.callerDisplayName.orEmpty().ifBlank { handle }
            _secondCallName.value = name
            _secondCallNumber.value = handle
        } else {
            _secondCallName.value = null
            _secondCallNumber.value = null
        }
    }

    fun mergeCalls() {
        val calls = _allCalls.value
        if (calls.size < 2) return

        val primaryCall = calls.firstOrNull { it.state == Call.STATE_ACTIVE } ?: calls.first()
        val secondaryCall = calls.firstOrNull { it !== primaryCall }

        if (primaryCall != null && secondaryCall != null) {
            try {
                primaryCall.conference(secondaryCall)
                Log.d(ICS_TAG, "Initiated Call.conference() for multi-party call merge")

                if (primaryCall.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true) {
                    primaryCall.mergeConference()
                }
                if (secondaryCall.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true) {
                    secondaryCall.mergeConference()
                }
            } catch (e: Exception) {
                Log.e(ICS_TAG, "mergeCalls failed", e)
            }
        }
    }

    fun swapCalls() {
        val calls = _allCalls.value
        if (calls.size < 2) return

        val activeCall = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
        val heldCall = calls.firstOrNull { it.state == Call.STATE_HOLDING }

        try {
            if (heldCall != null) {
                heldCall.unhold()
                activeCall?.hold()
                Log.d(ICS_TAG, "Swapped active and held calls")
            } else if (calls.size >= 2) {
                calls[0].hold()
                calls[1].unhold()
            }
        } catch (e: Exception) {
            Log.e(ICS_TAG, "swapCalls failed", e)
        }
    }

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    fun answer(asVideo: Boolean = isVideoCall.value) {
        val calls = instance?.calls.orEmpty()
        val activeCall = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
        val ringingCall = calls.firstOrNull { it.state == Call.STATE_RINGING } ?: _currentCall.value ?: return

        if (activeCall != null && activeCall !== ringingCall) {
            try {
                activeCall.hold()
            } catch (_: Exception) {
            }
        }

        _currentCall.value = ringingCall
        val incomingVideoState = ringingCall.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
        val isVideo = asVideo || _isVideoCall.value || VideoProfile.isVideo(incomingVideoState)
        val targetVideoState = if (isVideo) {
            VideoProfile.STATE_BIDIRECTIONAL
        } else {
            VideoProfile.STATE_AUDIO_ONLY
        }
        if (VideoProfile.isVideo(targetVideoState)) {
            _isVideoCall.value = true
            setSpeakerphone(true)
        }
        ringingCall.answer(targetVideoState)
    }

    fun answerAsAudioOnly() {
        val calls = instance?.calls.orEmpty()
        val activeCall = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
        val ringingCall = calls.firstOrNull { it.state == Call.STATE_RINGING } ?: _currentCall.value ?: return

        if (activeCall != null && activeCall !== ringingCall) {
            try {
                activeCall.hold()
            } catch (_: Exception) {
            }
        }

        _currentCall.value = ringingCall
        _isVideoCall.value = false
        ringingCall.answer(VideoProfile.STATE_AUDIO_ONLY)
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

    fun resolveCameraId(front: Boolean): String {
        val context = instance ?: return if (front) "1" else "0"
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return if (front) "1" else "0"
            val targetFacing = if (front) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    return id
                }
            }
        } catch (_: Exception) {
        }
        return if (front) "1" else "0"
    }

    // --- CARRIER VIDEO CALL (ViLTE / IMS) STATE & CONTROLS ---
    private val _isVideoCall = MutableStateFlow(false)
    val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

    private val _isVideoUpgradePending = MutableStateFlow(false)
    val isVideoUpgradePending: StateFlow<Boolean> = _isVideoUpgradePending.asStateFlow()

    private var upgradeTimeoutJob: Job? = null
    private var outgoingGraceJob: Job? = null

    private val _canUpgradeToVideo = MutableStateFlow(false)
    val canUpgradeToVideo: StateFlow<Boolean> = _canUpgradeToVideo.asStateFlow()

    private val _videoDowngradedNotice = MutableStateFlow<String?>(null)
    val videoDowngradedNotice: StateFlow<String?> = _videoDowngradedNotice.asStateFlow()

    private var requestedOutgoingVideo = false
    private var currentlyBoundCameraId: String? = null
    private var registeredVideoCall: InCallService.VideoCall? = null
    private var cachedPreviewSurface: Surface? = null
    private var cachedDisplaySurface: Surface? = null

    fun prepareOutgoingVideoCall() {
        requestedOutgoingVideo = true
        _isFrontCamera.value = true
        _isVideoCall.value = true
    }

    fun clearVideoDowngradedNotice() {
        _videoDowngradedNotice.value = null
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
            Log.d(ICS_TAG, "onSessionModifyRequestReceived videoState=$requestedState")
            if (VideoProfile.isVideo(requestedState)) {
                _incomingVideoUpgradeRequest.value = true
            } else {
                _isVideoUpgradePending.value = false
                _isVideoCall.value = false
                currentlyBoundCameraId = null
            }
        }

        override fun onSessionModifyResponseReceived(
            status: Int,
            requestedProfile: VideoProfile?,
            responseProfile: VideoProfile?,
        ) {
            Log.d(
                ICS_TAG,
                "onSessionModifyResponseReceived status=$status req=${requestedProfile?.videoState} resp=${responseProfile?.videoState}",
            )
            upgradeTimeoutJob?.cancel()
            upgradeTimeoutJob = null
            _isVideoUpgradePending.value = false

            val isSuccess = status == Connection.VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS &&
                    VideoProfile.isVideo(responseProfile?.videoState ?: VideoProfile.STATE_AUDIO_ONLY)

            if (isSuccess) {
                _isVideoCall.value = true
                setSpeakerphone(true)
                bindImsCameraAndSurfacesIfNeeded(_currentCall.value?.videoCall)
            } else {
                val currentTelecomVideo = VideoProfile.isVideo(
                    _currentCall.value?.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY,
                )
                _isVideoCall.value = currentTelecomVideo
                if (!currentTelecomVideo) {
                    currentlyBoundCameraId = null
                    if (VideoProfile.isVideo(requestedProfile?.videoState ?: VideoProfile.STATE_AUDIO_ONLY)) {
                        _videoDowngradedNotice.value = "Video request declined or unsupported by recipient carrier"
                    }
                }
            }
        }

        override fun onCallSessionEvent(event: Int) {
            Log.d(ICS_TAG, "onCallSessionEvent event=$event")
        }

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

    private fun bindImsCameraAndSurfacesIfNeeded(videoCall: InCallService.VideoCall?) {
        if (videoCall == null) return
        try {
            val targetCam = resolveCameraId(_isFrontCamera.value)
            if (currentlyBoundCameraId != targetCam) {
                videoCall.setCamera(targetCam)
                currentlyBoundCameraId = targetCam
                videoCall.requestCameraCapabilities()
            }
            cachedPreviewSurface?.takeIf { it.isValid }?.let {
                videoCall.setPreviewSurface(it)
            }
            cachedDisplaySurface?.takeIf { it.isValid }?.let {
                videoCall.setDisplaySurface(it)
            }
        } catch (e: Exception) {
            Log.e(ICS_TAG, "bindImsCameraAndSurfacesIfNeeded failed", e)
        }
    }

    /**
     * Synchronizes Telecom's real Call.Details videoState and IMS VideoCall instance
     * without prematurely killing outgoing or mid-call upgrade requests.
     */
    fun syncVideoCallState(call: Call?) {
        if (call == null) {
            requestedOutgoingVideo = false
            currentlyBoundCameraId = null
            registeredVideoCall = null
            upgradeTimeoutJob?.cancel()
            upgradeTimeoutJob = null
            outgoingGraceJob?.cancel()
            outgoingGraceJob = null
            _isVideoUpgradePending.value = false
            _isVideoCall.value = false
            _canUpgradeToVideo.value = false
            _incomingVideoUpgradeRequest.value = false
            return
        }

        val details = call.details
        val vState = details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
        @Suppress("DEPRECATION")
        val cState = call.state
        val videoCallObj = call.videoCall

        if (videoCallObj != null && videoCallObj !== registeredVideoCall) {
            registeredVideoCall = videoCallObj
            try {
                videoCallObj.registerCallback(videoCallCallback)
            } catch (e: Exception) {
                Log.e(ICS_TAG, "registerCallback failed", e)
            }
        }

        val localVtSupported = details?.can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) == true ||
                details?.can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX) == true
        val remoteVtSupported = details?.can(Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL) == true ||
                details?.can(Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX) == true

        // Allow upgrade button only when both sides report VT support (same operator IMS)
        _canUpgradeToVideo.value = localVtSupported && remoteVtSupported && videoCallObj != null

        val telecomIsVideo = VideoProfile.isVideo(vState)

        when {
            telecomIsVideo -> {
                // Carrier confirmed live video session
                requestedOutgoingVideo = false
                outgoingGraceJob?.cancel()
                outgoingGraceJob = null
                upgradeTimeoutJob?.cancel()
                upgradeTimeoutJob = null
                _isVideoUpgradePending.value = false
                _isVideoCall.value = true
                if (!isSpeaker.value) {
                    setSpeakerphone(true)
                }
                bindImsCameraAndSurfacesIfNeeded(videoCallObj)
            }

            _isVideoUpgradePending.value -> {
                // Mid-call upgrade request is currently waiting for recipient/IMS response.
                // Do NOT overwrite _isVideoCall to false here!
                _isVideoCall.value = true
                bindImsCameraAndSurfacesIfNeeded(videoCallObj)
            }

            requestedOutgoingVideo && (
                    cState == Call.STATE_SELECT_PHONE_ACCOUNT ||
                            cState == Call.STATE_DIALING ||
                            cState == Call.STATE_CONNECTING
                    ) -> {
                // Outgoing video call is still ringing/connecting (note: localVtSupported is often
                // not populated until STATE_ACTIVE on Jio/Airtel, so do not gate dialing on it!)
                _isVideoCall.value = true
                if (!isSpeaker.value) {
                    setSpeakerphone(true)
                }
                bindImsCameraAndSurfacesIfNeeded(videoCallObj)
            }

            requestedOutgoingVideo && cState == Call.STATE_ACTIVE -> {
                // Call just answered! On Samsung/Qualcomm IMS, videoState can take 300-800ms after
                // STATE_ACTIVE to flip to STATE_BIDIRECTIONAL, OR if the carrier downgraded to voice,
                // we send an immediate upgrade request if supported, otherwise fall back after 1.5s.
                _isVideoCall.value = true
                bindImsCameraAndSurfacesIfNeeded(videoCallObj)
                if (outgoingGraceJob == null) {
                    outgoingGraceJob = managerScope.launch {
                        // If videoCallObj exists and both sides support VT, nudge IMS to bidirectional
                        if (videoCallObj != null && localVtSupported && remoteVtSupported) {
                            try {
                                videoCallObj.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL))
                            } catch (_: Exception) {
                            }
                        }
                        delay(1500L)
                        val latestCall = _currentCall.value
                        val nowVideo = VideoProfile.isVideo(
                            latestCall?.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY,
                        )
                        requestedOutgoingVideo = false
                        outgoingGraceJob = null
                        if (!nowVideo && !_isVideoUpgradePending.value) {
                            _isVideoCall.value = false
                            currentlyBoundCameraId = null
                            _videoDowngradedNotice.value =
                                "Recipient network does not support carrier video — connected via voice"
                        }
                    }
                }
            }

            else -> {
                _isVideoCall.value = false
                currentlyBoundCameraId = null
            }
        }
    }

    fun toggleVideoCall() {
        val call = _currentCall.value ?: return
        val videoCall = call.videoCall
        val telecomIsVideo = VideoProfile.isVideo(call.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY)

        if (!telecomIsVideo && !_isVideoUpgradePending.value) {
            if (videoCall == null || !_canUpgradeToVideo.value) {
                _videoDowngradedNotice.value =
                    "Carrier video call is only supported between compatible VoLTE/ViLTE networks"
                return
            }

            setSpeakerphone(true)
            _isVideoUpgradePending.value = true
            _isVideoCall.value = true

            try {
                videoCall.registerCallback(videoCallCallback)
                registeredVideoCall = videoCall
                val camId = resolveCameraId(_isFrontCamera.value)
                videoCall.setCamera(camId)
                currentlyBoundCameraId = camId
                cachedPreviewSurface?.takeIf { it.isValid }?.let {
                    videoCall.setPreviewSurface(it)
                }
                videoCall.requestCameraCapabilities()
                videoCall.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL))
                Log.d(ICS_TAG, "Sent mid-call ViLTE upgrade request (STATE_BIDIRECTIONAL)")
            } catch (e: Exception) {
                Log.e(ICS_TAG, "toggleVideoCall failed", e)
                _isVideoUpgradePending.value = false
                _isVideoCall.value = false
                _videoDowngradedNotice.value = "Unable to start carrier video call"
                return
            }

            upgradeTimeoutJob?.cancel()
            upgradeTimeoutJob = managerScope.launch {
                delay(15000L)
                if (_isVideoUpgradePending.value) {
                    _isVideoUpgradePending.value = false
                    _isVideoCall.value = false
                    currentlyBoundCameraId = null
                    _videoDowngradedNotice.value = "Video call request timed out"
                }
            }
        } else {
            cancelVideoUpgradeRequest()
        }
    }

    fun cancelVideoUpgradeRequest() {
        upgradeTimeoutJob?.cancel()
        upgradeTimeoutJob = null
        outgoingGraceJob?.cancel()
        outgoingGraceJob = null
        requestedOutgoingVideo = false
        _isVideoUpgradePending.value = false
        _isVideoCall.value = false
        currentlyBoundCameraId = null
        try {
            val videoCall = _currentCall.value?.videoCall
            videoCall?.setPreviewSurface(null)
            videoCall?.setDisplaySurface(null)
            videoCall?.setCamera(null)
            videoCall?.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_AUDIO_ONLY))
        } catch (_: Exception) {
        }
    }

    fun respondToVideoUpgradeRequest(accept: Boolean) {
        val call = _currentCall.value ?: return
        upgradeTimeoutJob?.cancel()
        upgradeTimeoutJob = null
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
                bindImsCameraAndSurfacesIfNeeded(call.videoCall)
            }
            call.videoCall?.sendSessionModifyResponse(VideoProfile(targetState))
        } catch (_: Exception) {
        }
    }

    fun flipVideoCamera() {
        val nextFront = !_isFrontCamera.value
        _isFrontCamera.value = nextFront
        val camId = resolveCameraId(nextFront)
        try {
            _currentCall.value?.videoCall?.let { vc ->
                vc.setCamera(camId)
                currentlyBoundCameraId = camId
                vc.requestCameraCapabilities()
            }
        } catch (_: Exception) {
        }
    }

    fun bindRemoteDisplaySurface(surface: Surface?) {
        cachedDisplaySurface = surface
        try {
            _currentCall.value?.videoCall?.setDisplaySurface(surface)
        } catch (_: Exception) {
        }
    }

    fun bindLocalPreviewSurface(surface: Surface?) {
        cachedPreviewSurface = surface
        try {
            val videoCall = _currentCall.value?.videoCall ?: return
            if (surface != null && surface.isValid) {
                val camId = resolveCameraId(_isFrontCamera.value)
                if (currentlyBoundCameraId != camId) {
                    videoCall.setCamera(camId)
                    currentlyBoundCameraId = camId
                }
                videoCall.setPreviewSurface(surface)
                videoCall.requestCameraCapabilities()
            } else {
                videoCall.setPreviewSurface(null)
            }
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
        requestedOutgoingVideo = false
        currentlyBoundCameraId = null
        registeredVideoCall = null
        cachedPreviewSurface = null
        cachedDisplaySurface = null
        upgradeTimeoutJob?.cancel()
        upgradeTimeoutJob = null
        outgoingGraceJob?.cancel()
        outgoingGraceJob = null
        _isVideoUpgradePending.value = false
        _isVideoCall.value = false
        _canUpgradeToVideo.value = false
        _videoDowngradedNotice.value = null
        _incomingVideoUpgradeRequest.value = false
        isMuted.value = false
        isSpeaker.value = false
        isOnHold.value = false
        callDurationSeconds.value = 0L
        CallBridge.resetIdentity()
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
        CallRingtonePlayer.stop(this)
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

    private fun extractCnapDisplayName(call: Call?): String? {
        val details = call?.details ?: return null
        if (details.callerDisplayNamePresentation != TelecomManager.PRESENTATION_ALLOWED) {
            return null
        }
        val rawName = details.callerDisplayName?.trim().orEmpty()
        if (rawName.isBlank() || rawName.equals("Unknown", ignoreCase = true)) {
            return null
        }
        val rawNumber = details.handle?.schemeSpecificPart.orEmpty()
        if (PhoneNormalizer.normalizeDigits(rawName) == PhoneNormalizer.normalizeDigits(rawNumber) && rawName.length > 5) {
            return null
        }
        return rawName
    }

    private fun updateAndResolveCaller(call: Call) {
        val rawNumber = call.details?.handle?.schemeSpecificPart?.trim().orEmpty()
        val cnapName = extractCnapDisplayName(call)

        if (rawNumber.isNotBlank()) {
            CallBridge.callerNumber.value = rawNumber
            if (CallBridge.callerName.value == "Unknown Caller" || CallBridge.callerName.value.isBlank()) {
                if (cnapName != null) {
                    CallBridge.callerName.value = cnapName
                    CallBridge.isCnapVerified.value = true
                } else {
                    CallBridge.callerName.value = rawNumber
                    CallBridge.isCnapVerified.value = false
                }
            }
        }
        resolveJob?.cancel()
        resolveJob = serviceScope.launch(Dispatchers.IO) {
            val resolved = CallerIdResolver.resolveCaller(applicationContext, rawNumber)
            val (finalName, isCnap) = when {
                resolved.isSavedContact -> resolved.displayName to false
                cnapName != null -> cnapName to true
                else -> resolved.displayName to false
            }

            CallBridge.updateCallerIdentity(
                name = finalName,
                number = rawNumber,
                photoUri = resolved.photoUri,
                isCnap = isCnap,
            )

            val displayResolved = resolved.copy(displayName = finalName)

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
                    displayResolved,
                    overlayIsShowing = incomingOverlayShowing,
                )
            } else if (
                currentState == Call.STATE_SELECT_PHONE_ACCOUNT ||
                currentState == Call.STATE_DIALING ||
                currentState == Call.STATE_CONNECTING ||
                currentState == Call.STATE_ACTIVE ||
                currentState == Call.STATE_HOLDING
            ) {
                CallNotificationManager.showOngoingCallNotification(this@AetherInCallService, call, displayResolved)
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
            CallManager.syncCallsList(calls)
            @Suppress("DEPRECATION")
            val state = call.state
            if (incomingOverlayShowing && state == Call.STATE_RINGING) {
                val isVideo = CallManager.isVideoCall.value || VideoProfile.isVideo(details.videoState)
                if (isVideo) {
                    incomingOverlayShowing = false
                    IncomingCallOverlayService.stopOverlay(this@AetherInCallService)
                    val number = details.handle?.schemeSpecificPart.orEmpty()
                    launchFullScreenInCall(call, number)
                }
            }
            if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                updateAndResolveCaller(call)
            }
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) {
            super.onConferenceableCallsChanged(call, conferenceableCalls)
            Log.d(ICS_TAG, "onConferenceableCallsChanged count=${conferenceableCalls.size}")
            CallManager.syncCallsList(calls)
        }

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(ICS_TAG, "onStateChanged state=$state")
            CallManager.updateState(state)
            CallManager.syncVideoCallState(call)
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
                    CallRingtonePlayer.stop(this@AetherInCallService)
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
        CallManager.syncCallsList(calls)
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
                handleIncomingRinging(call, number, initialResolved)

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
        } else {
            val remainingCall = calls.first()
            CallManager.updateCall(remainingCall)
            if (remainingCall.state == Call.STATE_HOLDING) {
                try {
                    remainingCall.unhold()
                } catch (_: Exception) {
                }
            }
            CallManager.syncCallsList(calls)
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
        CallRingtonePlayer.start(this)
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

    private fun silenceActiveCallRinger() {
        try {
            CallRingtonePlayer.silence(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}