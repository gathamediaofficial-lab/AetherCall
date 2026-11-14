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
import android.telecom.VideoProfile
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        // Only cancel notifications if there is no active call in CallManager
        if (CallManager.currentCall.value == null) {
            CallNotificationManager.cancelNotification(this)
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && CallBridge.callState.value == Call.STATE_RINGING) {
            val settings = runBlocking { AppSettingsManager(this@InCallActivity).settingsFlow.first() }
            if (settings.volumeUpToAnswer) {
                CallBridge.answerCall()
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
            val isHeadset = CallBridge.isMuted.value

            if (isNear && !isSpeaker && !isHeadset) {
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
            proximityWakeLock?.acquire(10 * 60 * 1000L)
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
    val isCarrierVideoCall by CallManager.isVideoCall.collectAsState()
    val isFrontCamera by CallManager.isFrontCamera.collectAsState()
    val incomingVideoUpgradeRequest by CallManager.incomingVideoUpgradeRequest.collectAsState()
    val isVideoCall = isCarrierVideoCall || ((currentCall?.details?.videoState ?: 0) != VideoProfile.STATE_AUDIO_ONLY)
    val simLabel by CallBridge.simLabel.collectAsState()
    val settingsManager = remember(context) { AppSettingsManager(context) }
    val userSettings by settingsManager.settingsFlow.collectAsState(initial = UserAppSettings())
    val coroutineScope = rememberCoroutineScope()

    // Load live Quick Reply templates from Room
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

    val rawHandle = currentCall?.details?.handle?.schemeSpecificPart.orEmpty()
    val handle = rawHandle.ifBlank { callerNumberState.ifBlank { fallbackIntentNumber } }

    var posterPhotoUri by remember { mutableStateOf<String?>(callerPhotoUriState) }
    var contactName by remember {
        mutableStateOf(
            callerNameState.takeIf { it.isNotBlank() && it != "Unknown Caller" }
                ?: handle.ifBlank { "Unknown Caller" },
        )
    }

    // Keep synced with CallBridge updates
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

    // Normalized 10-digit lookup across PhoneLookup, ContactsRepository, and ContactPosterDao
    LaunchedEffect(handle, currentCall) {
        val targetNumber = currentCall?.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: handle
        if (targetNumber.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val targetNorm = PhoneNormalizer.normalizeDigits(targetNumber)
                val db = AetherDatabase.getDatabase(context)

                // 1. Check custom poster by exact or normalized number
                val poster = db.contactPosterDao().getPosterForNumber(targetNumber)
                    ?: if (targetNorm.isNotEmpty()) db.contactPosterDao().getPosterForNumber(targetNorm) else null

                // 2. Fast PhoneLookup + normalized fallback
                val resolved = CallerIdResolver.resolveCaller(context, targetNumber)

                // 3. Full ContactsRepository check for callingCardPhotoUri & multi-number contacts
                val matchedContact = ContactsRepository.findContactByNumber(context, targetNumber)

                val finalName = when {
                    !poster?.customNickname.isNullOrBlank() -> poster!!.customNickname!!
                    !matchedContact?.displayName.isNullOrBlank() -> matchedContact!!.displayName
                    resolved.isSaved && resolved.displayName.isNotBlank() -> resolved.displayName
                    else -> targetNumber
                }

                val finalPhoto = poster?.customPhotoUri
                    ?: matchedContact?.callingCardPhotoUri
                    ?: matchedContact?.photoUri
                    ?: resolved.photoUri

                withContext(Dispatchers.Main) {
                    contactName = finalName
                    posterPhotoUri = finalPhoto
                    CallBridge.updateCallerIdentity(finalName, targetNumber, finalPhoto)
                }
            }
        }
    }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    // Haptic vibration feedback on Call Answer or End
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

    // 1-second coroutine ticker for active call duration
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

    // Auto-finish ONLY when the call is truly disconnected and removed from CallManager
    var hasSeenActiveOrRingingCall by remember { mutableStateOf(false) }
    LaunchedEffect(callState, currentCall) {
        if (currentCall != null && callState != Call.STATE_DISCONNECTED) {
            hasSeenActiveOrRingingCall = true
        }
        if (hasSeenActiveOrRingingCall && callState == Call.STATE_DISCONNECTED && currentCall == null) {
            delay(350L)
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
    } else if (isCarrierVideoCall) {
        CarrierVideoCallScreen(
            callerName = contactName,
            callerNumber = handle,
            callerPhotoUri = posterPhotoUri,
            simLabel = simLabel,
            callDurationText = durationText,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isFrontCamera = isFrontCamera,
            onBindRemoteSurface = { surface -> CallManager.bindRemoteDisplaySurface(surface) },
            onBindPreviewSurface = { surface -> CallManager.bindLocalPreviewSurface(surface) },
            onFlipCamera = { CallManager.flipVideoCamera() },
            onMuteToggle = { CallBridge.toggleMute() },
            onSpeakerToggle = { CallBridge.toggleSpeaker() },
            onSwitchToVoice = { CallManager.toggleVideoCall() },
            onEndCallClick = { CallBridge.disconnectCall() },
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
            onVideoToggle = { CallManager.toggleVideoCall() },
            onDtmfDigitPress = { digit -> CallBridge.playDtmfTone(digit) },
            onEndCallClick = { CallBridge.disconnectCall() },
            onCallAgainClick = { numberToRedial ->
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