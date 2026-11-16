package com.aether.call.ui

import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aether.call.data.AetherDatabase
import com.aether.call.data.AppSettingsManager
import com.aether.call.data.ContactsRepository
import com.aether.call.data.UserAppSettings
import com.aether.call.telecom.AudioVisualizerEngine
import com.aether.call.telecom.CallBridge
import com.aether.call.telecom.CallManager
import com.aether.call.telecom.CallNotificationManager
import com.aether.call.telecom.CallerIdResolver
import com.aether.call.util.PhoneNormalizer
import com.aether.call.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale

const val ACTION_PIP_MUTE = "com.aether.call.PIP_MUTE"
const val ACTION_PIP_SPEAKER = "com.aether.call.PIP_SPEAKER"
const val ACTION_PIP_END_CALL = "com.aether.call.PIP_END_CALL"

class InCallActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val isInPipModeState = mutableStateOf(false)
    private val intentPhoneNumberState = mutableStateOf("")

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_MUTE -> CallBridge.toggleMute()
                ACTION_PIP_SPEAKER -> CallBridge.toggleSpeaker()
                ACTION_PIP_END_CALL -> {
                    CallBridge.disconnectCall()
                    finishAndRemoveTask()
                }
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF && CallBridge.callState.value == Call.STATE_ACTIVE) {
                val settings = runBlocking { AppSettingsManager(this@InCallActivity).settingsFlow.first() }
                if (settings.powerKeyEndsCall) {
                    CallBridge.disconnectCall()
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        com.aether.call.telecom.IncomingCallOverlayService.stopOverlay(this)
        enableEdgeToEdge()

        intentPhoneNumberState.value = intent?.getStringExtra("CALL_NUMBER").orEmpty()
        handleAutoAnswerIfRequested(intent)
        initProximitySensor()

        setContent {
            InCallActivityContainer(
                isInPipMode = isInPipModeState.value,
                fallbackIntentNumber = intentPhoneNumberState.value,
                onFinish = { finishAndRemoveTask() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.aether.call.telecom.IncomingCallOverlayService.stopOverlay(this)
        val newNum = intent.getStringExtra("CALL_NUMBER").orEmpty()
        if (newNum.isNotBlank()) {
            intentPhoneNumberState.value = newNum
        }
        handleAutoAnswerIfRequested(intent)
    }

    private fun handleAutoAnswerIfRequested(startIntent: Intent?) {
        if (startIntent?.getBooleanExtra("AUTO_ANSWER_CALL", false) == true) {
            startIntent.removeExtra("AUTO_ANSWER_CALL")
            CallBridge.answerCall()
            CallNotificationManager.cancelIncomingCallNotification(this)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_MUTE)
            addAction(ACTION_PIP_SPEAKER)
            addAction(ACTION_PIP_END_CALL)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        com.aether.call.telecom.IncomingCallOverlayService.stopOverlay(this)
        proximitySensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        releaseProximityWakeLock()
    }

    override fun onStop() {
        try {
            unregisterReceiver(pipActionReceiver)
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
        releaseProximityWakeLock()
    }

    override fun onDestroy() {
        AudioVisualizerEngine.stop()
        releaseProximityWakeLock()
        proximityWakeLock = null
        if (CallManager.currentCall.value == null) {
            CallNotificationManager.cancelNotification(this)
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (CallBridge.callState.value == Call.STATE_RINGING) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                val settings = runBlocking { AppSettingsManager(this@InCallActivity).settingsFlow.first() }
                if (settings.volumeUpToAnswer) {
                    CallBridge.answerCall()
                    return true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                com.aether.call.telecom.CallRingtonePlayer.silence(this)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val callState = CallBridge.callState.value
        if (callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    enterPictureInPictureMode(
                        PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build(),
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isNear = distance < maxRange && distance < 5f

            val isSpeaker = CallBridge.isSpeakerOn.value
            val isVideo = CallManager.isVideoCall.value ||
                    CallManager.isVideoUpgradePending.value ||
                    VideoProfile.isVideo(CallManager.currentCall.value?.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY)

            if (isNear && !isSpeaker && !isVideo) {
                acquireProximityWakeLock()
            } else {
                releaseProximityWakeLock()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun initProximitySensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val powerManager = (getSystemService(POWER_SERVICE) as? PowerManager) ?: return
        @Suppress("DEPRECATION")
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            @Suppress("DEPRECATION")
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "AetherCall::ProximityWakeLock",
            )
        }
    }

    private fun acquireProximityWakeLock() {
        if (proximityWakeLock?.isHeld == false) {
            proximityWakeLock?.acquire(600_000L)
        }
    }

    private fun releaseProximityWakeLock() {
        if (proximityWakeLock?.isHeld == true) {
            @Suppress("DEPRECATION")
            proximityWakeLock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }
}

@Composable
fun InCallActivityContainer(
    isInPipMode: Boolean = false,
    fallbackIntentNumber: String = "",
    onFinish: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentCall by CallManager.currentCall.collectAsState()
    val managerCallState by CallManager.callState.collectAsState()
    val bridgeCallState by CallBridge.callState.collectAsState()
    val callState = if (currentCall != null && bridgeCallState == Call.STATE_DISCONNECTED) {
        managerCallState
    } else {
        bridgeCallState
    }
    val isMuted by CallBridge.isMuted.collectAsState()
    val isSpeakerOn by CallBridge.isSpeakerOn.collectAsState()
    val isHoldOn by CallBridge.isHoldOn.collectAsState()
    val isRecording by CallBridge.isRecording.collectAsState()
    val canMergeCalls by CallBridge.canMergeCalls.collectAsState()
    val canSwapCalls by CallBridge.canSwapCalls.collectAsState()
    val isConferenceCall by CallBridge.isConferenceCall.collectAsState()
    val secondCallName by CallBridge.secondCallName.collectAsState()
    val secondCallNumber by CallBridge.secondCallNumber.collectAsState()
    val isCarrierVideoCall by CallManager.isVideoCall.collectAsState()
    val isVideoUpgradePending by CallManager.isVideoUpgradePending.collectAsState()
    val canUpgradeToVideo by CallManager.canUpgradeToVideo.collectAsState()
    val isFrontCamera by CallManager.isFrontCamera.collectAsState()
    val incomingVideoUpgradeRequest by CallManager.incomingVideoUpgradeRequest.collectAsState()
    val isVideoCall = isCarrierVideoCall || VideoProfile.isVideo(currentCall?.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY)
    val simLabel by CallBridge.simLabel.collectAsState()
    val settingsManager = remember(context) { AppSettingsManager(context) }
    val userSettings by settingsManager.settingsFlow.collectAsState(initial = UserAppSettings())
    val coroutineScope = rememberCoroutineScope()

    val quickRepliesFlow = remember(context) {
        AetherDatabase.getDatabase(context).quickReplyDao().getAllTemplates().map { list ->
            list.map { it.messageText }.ifEmpty {
                listOf(
                    "Can't talk now. What's up?",
                    "I'll call you right back.",
                    "I'm in a meeting.",
                    "Please send a text message.",
                )
            }
        }
    }
    val quickReplies by quickRepliesFlow.collectAsState(
        initial = listOf(
            "Can't talk now. What's up?",
            "I'll call you right back.",
            "I'm in a meeting.",
            "Please send a text message.",
        ),
    )

    val callerNameState by CallBridge.callerName.collectAsState()
    val callerNumberState by CallBridge.callerNumber.collectAsState()
    val callerPhotoUriState by CallBridge.callerPhotoUri.collectAsState()
    val videoDowngradedNotice by CallManager.videoDowngradedNotice.collectAsState()

    LaunchedEffect(videoDowngradedNotice) {
        val notice = videoDowngradedNotice
        if (!notice.isNullOrBlank()) {
            context.showToast(notice, Toast.LENGTH_LONG)
            CallManager.clearVideoDowngradedNotice()
        }
    }

    val rawHandle = currentCall?.details?.handle?.schemeSpecificPart.orEmpty()
    val handle = rawHandle.ifBlank { callerNumberState.ifBlank { fallbackIntentNumber } }

    var posterPhotoUri by remember { mutableStateOf<String?>(callerPhotoUriState) }
    var contactName by remember {
        mutableStateOf(
            callerNameState.takeIf { it.isNotBlank() && it != "Unknown Caller" }
                ?: handle.ifBlank { "Unknown Caller" },
        )
    }

    LaunchedEffect(callerNameState) {
        if (callerNameState.isNotBlank() && callerNameState != "Unknown Caller" && callerNameState != handle) {
            contactName = callerNameState
        }
    }

    LaunchedEffect(callerPhotoUriState) {
        if (!callerPhotoUriState.isNullOrBlank()) {
            posterPhotoUri = callerPhotoUriState
        }
    }

    LaunchedEffect(handle, currentCall) {
        val targetNumber = currentCall?.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: handle
        if (targetNumber.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val targetNorm = PhoneNormalizer.normalizeDigits(targetNumber)
                val db = AetherDatabase.getDatabase(context)

                val poster = db.contactPosterDao().getPosterForNumber(targetNumber)
                    ?: if (targetNorm.isNotEmpty()) db.contactPosterDao().getPosterForNumber(targetNorm) else null

                val resolved = CallerIdResolver.resolveCaller(context, targetNumber)
                val matchedContact = ContactsRepository.findContactByNumber(context, targetNumber)
                val cnapName = currentCall?.let { c ->
                    val details = c.details
                    if (details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                        details.callerDisplayName?.trim()?.takeIf { name ->
                            name.isNotBlank() && !name.equals("Unknown", ignoreCase = true)
                        }
                    } else null
                }

                val (finalName, isCnap) = when {
                    !poster?.customNickname.isNullOrBlank() -> poster!!.customNickname!! to false
                    !matchedContact?.displayName.isNullOrBlank() -> matchedContact!!.displayName to false
                    resolved.isSaved && resolved.displayName.isNotBlank() -> resolved.displayName to false
                    cnapName != null -> cnapName to true
                    else -> targetNumber to false
                }

                val finalPhoto = poster?.customPhotoUri
                    ?: matchedContact?.callingCardPhotoUri
                    ?: matchedContact?.photoUri
                    ?: resolved.photoUri

                withContext(Dispatchers.Main) {
                    contactName = finalName
                    posterPhotoUri = finalPhoto
                    CallBridge.updateCallerIdentity(finalName, targetNumber, finalPhoto, isCnap = isCnap)
                }
            }
        }
    }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(callState) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (callState == Call.STATE_ACTIVE && userSettings.vibrateOnAnswer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60L)
            }
        } else if (callState == Call.STATE_DISCONNECTED && userSettings.vibrateOnEnd) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60L)
            }
        }
    }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    var wasVideoSession by remember { mutableStateOf(false) }

    LaunchedEffect(currentCall, isVideoCall) {
        if (isVideoCall) {
            wasVideoSession = true
        } else if (currentCall != null && (callState == Call.STATE_RINGING || callState == Call.STATE_DIALING || callState == Call.STATE_ACTIVE)) {
            wasVideoSession = false
        }
    }

    var hasSeenActiveOrRingingCall by remember { mutableStateOf(false) }
    LaunchedEffect(callState, currentCall) {
        if (currentCall != null && callState != Call.STATE_DISCONNECTED) {
            hasSeenActiveOrRingingCall = true
        }
        if (hasSeenActiveOrRingingCall && callState == Call.STATE_DISCONNECTED && currentCall == null) {
            delay(4000L)
            onFinish()
        }
    }

    val durationText = formatDurationText(elapsedSeconds)

    if (isInPipMode) {
        PipCallingContent(
            callerName = contactName,
            callerNumber = handle,
            durationText = durationText,
            posterPhotoUri = posterPhotoUri,
        )
    } else if (isVideoCall || isVideoUpgradePending || (wasVideoSession && (callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED))) {
        CarrierVideoCallScreen(
            callState = callState,
            callerName = contactName,
            callerNumber = handle,
            callerPhotoUri = posterPhotoUri,
            simLabel = simLabel,
            callDurationText = durationText,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isFrontCamera = isFrontCamera,
            isVideoUpgradePending = isVideoUpgradePending,
            onBindRemoteSurface = { surface -> CallManager.bindRemoteDisplaySurface(surface) },
            onBindPreviewSurface = { surface -> CallManager.bindLocalPreviewSurface(surface) },
            onAnswerVideoClick = { CallManager.answer(asVideo = true) },
            onAnswerAudioOnlyClick = { CallManager.answerAsAudioOnly() },
            onDeclineClick = {
                CallBridge.rejectCall()
                onFinish()
            },
            onFlipCamera = { CallManager.flipVideoCamera() },
            onMuteToggle = { CallBridge.toggleMute() },
            onSpeakerToggle = { CallBridge.toggleSpeaker() },
            onSwitchToVoice = { CallManager.toggleVideoCall() },
            onCancelVideoUpgrade = { CallManager.cancelVideoUpgradeRequest() },
            onEndCallClick = { CallBridge.disconnectCall() },
            onCallAgainClick = {
                wasVideoSession = false
                if (handle.isNotBlank()) {
                    com.aether.call.data.CallLogRepository.placeCarrierVideoCall(context, handle)
                }
            },
            onDismissEndedScreen = { onFinish() },
        )
    } else {
        InCallScreen(
            callState = callState,
            callerName = contactName,
            callerNumber = handle,
            callerPhotoUri = posterPhotoUri,
            simLabel = simLabel,
            callDurationText = durationText,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isHoldOn = isHoldOn,
            isRecording = isRecording,
            isVideoCall = isVideoCall,
            isFrontCamera = isFrontCamera,
            incomingVideoUpgradeRequest = incomingVideoUpgradeRequest,
            canMergeCalls = canMergeCalls,
            canSwapCalls = canSwapCalls,
            isConferenceCall = isConferenceCall,
            secondCallName = secondCallName,
            secondCallNumber = secondCallNumber,
            onMergeCalls = { CallBridge.mergeCalls() },
            onSwapCalls = { CallBridge.swapCalls() },
            onBindRemoteSurface = { surface -> CallManager.bindRemoteDisplaySurface(surface) },
            onBindPreviewSurface = { surface -> CallManager.bindLocalPreviewSurface(surface) },
            onFlipCamera = { CallManager.flipVideoCamera() },
            onRespondVideoUpgrade = { accept -> CallManager.respondToVideoUpgradeRequest(accept) },
            quickReplies = quickReplies,
            backgroundTheme = userSettings.callBackgroundTheme,
            customCallBackgroundUri = userSettings.customCallBackgroundUri,
            incomingCallLayoutStyle = userSettings.incomingCallLayoutStyle,
            onAnswerClick = { CallBridge.answerCall() },
            onDeclineClick = {
                CallBridge.rejectCall()
                onFinish()
            },
            onSendQuickReply = { message ->
                CallBridge.rejectWithSms(context, message)
                onFinish()
            },
            onMuteToggle = { CallBridge.toggleMute() },
            onSpeakerToggle = { CallBridge.toggleSpeaker() },
            onHoldToggle = { CallBridge.toggleHold() },
            onRecordToggle = { CallBridge.toggleRecording(context) },
            onTransferRecordingClick = { CallBridge.transferLastRecording(context) },
            onVideoToggle = {
                if (canUpgradeToVideo) {
                    CallManager.toggleVideoCall()
                } else {
                    context.showToast("Video call is not supported on this network connection", Toast.LENGTH_SHORT)
                }
            },
            onDtmfDigitPress = { digit -> CallBridge.playDtmfTone(digit) },
            onEndCallClick = { CallBridge.disconnectCall() },
            onBlockAndReportSpam = { numberToBlock, reason ->
                com.aether.call.data.CallLogRepository.blockAndReportSpam(context, numberToBlock, reason)
                CallBridge.disconnectCall()
            },
            onCallAgainClick = { numberToRedial ->
                wasVideoSession = false
                if (numberToRedial.isNotBlank()) {
                    com.aether.call.data.CallLogRepository.placeCarrierCall(context, numberToRedial)
                }
            },
            onDismissEndedScreen = { onFinish() },
            onUpdateBackgroundTheme = { newTheme ->
                coroutineScope.launch {
                    settingsManager.updateCallBackgroundTheme(newTheme)
                }
            },
            onUpdateCustomBackgroundPhoto = { newUri ->
                coroutineScope.launch {
                    settingsManager.updateCustomCallBackgroundUri(newUri)
                }
            },
        )
    }

    if (incomingVideoUpgradeRequest) {
        AlertDialog(
            onDismissRequest = { CallManager.respondToVideoUpgradeRequest(false) },
            title = { Text("Switch to Video Call?") },
            text = { Text("$contactName is requesting to switch this call to HD Video.") },
            confirmButton = {
                TextButton(onClick = { CallManager.respondToVideoUpgradeRequest(true) }) {
                    Text("Accept", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { CallManager.respondToVideoUpgradeRequest(false) }) {
                    Text("Decline", color = Color(0xFFEF4444))
                }
            },
            containerColor = Color(0xFF1E2024),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
        )
    }

    if (callState == Call.STATE_SELECT_PHONE_ACCOUNT) {
        val activeSims = remember(context) { com.aether.call.telecom.SimManager.getActiveSims(context) }
        AlertDialog(
            onDismissRequest = { CallBridge.disconnectCall() },
            containerColor = Color(0xFF1E2024),
            shape = RoundedCornerShape(26.dp),
            title = {
                Column {
                    Text(
                        text = "Select SIM for call",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (handle.isNotBlank()) {
                        Text(
                            text = contactName.ifBlank { handle },
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeSims.forEach { sim ->
                        Surface(
                            onClick = {
                                CallManager.selectSimForWaitingCall(context, sim.slotIndex)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF141619),
                            border = BorderStroke(1.dp, Color(0xFF2E3138)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF10B981).copy(alpha = 0.16f),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SimCard,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "SIM ${sim.slotIndex + 1} • ${sim.carrierName}",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Slot ${sim.slotIndex + 1}",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { CallBridge.disconnectCall() }) {
                    Text("Cancel", color = Color(0xFF8E8E93))
                }
            },
        )
    }
}

@Composable
private fun PipCallingContent(
    callerName: String,
    callerNumber: String,
    durationText: String,
    posterPhotoUri: String?,
) {
    val context = LocalContext.current
    val isMuted by CallBridge.isMuted.collectAsState()
    val amplitude by AudioVisualizerEngine.rawAmplitude.collectAsState()

    Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (posterPhotoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(posterPhotoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xDD0F172A)),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                ) {
                    val initial = callerName.trimStart().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = durationText,
                    color = if (isMuted) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(4.dp))

                PipEqualizerCanvas(
                    targetAmplitude = amplitude,
                    isMuted = isMuted,
                    barCount = 5,
                    activeColor = Color(0xFF10B981),
                    mutedColor = Color(0xFFEF4444),
                    modifier = Modifier.size(width = 48.dp, height = 16.dp),
                )
            }
        }
    }
}

private fun formatDurationText(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}