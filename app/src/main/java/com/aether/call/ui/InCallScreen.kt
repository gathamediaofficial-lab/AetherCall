package com.aether.call.ui

import android.content.Intent
import android.net.Uri
import android.telecom.Call
import android.view.Surface
import android.widget.Toast
import com.aether.call.util.showToast
import androidx.compose.runtime.collectAsState
import com.aether.call.telecom.CallManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    callState: Int,
    callerName: String,
    callerNumber: String,
    callerPhotoUri: String? = null,
    simLabel: String = "IND-JIO",
    callDurationText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isHoldOn: Boolean,
    isRecording: Boolean = false,
    isVideoCall: Boolean = false,
    isFrontCamera: Boolean = true,
    incomingVideoUpgradeRequest: Boolean = false,
    quickReplies: List<String> = listOf(
        "Can't talk now. What's up?",
        "I'll call you right back.",
        "I'm in a meeting.",
        "Please send a text message.",
    ),
    backgroundTheme: String = "GRAD_AUTO_AVATAR",
    customCallBackgroundUri: String? = null,
    incomingCallLayoutStyle: String = "SLIDE_TO_ANSWER",
    onAnswerClick: () -> Unit,
    onDeclineClick: () -> Unit,
    onSendQuickReply: (String) -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHoldToggle: () -> Unit,
    onRecordToggle: () -> Unit = {},
    onTransferRecordingClick: () -> Unit = {},
    onVideoToggle: () -> Unit = {},
    onBindRemoteSurface: (Surface?) -> Unit = {},
    onBindPreviewSurface: (Surface?) -> Unit = {},
    onFlipCamera: () -> Unit = {},
    onRespondVideoUpgrade: (Boolean) -> Unit = {},
    onDtmfDigitPress: (Char) -> Unit,
    canMergeCalls: Boolean = false,
    canSwapCalls: Boolean = false,
    isConferenceCall: Boolean = false,
    secondCallName: String? = null,
    secondCallNumber: String? = null,
    onMergeCalls: () -> Unit = {},
    onSwapCalls: () -> Unit = {},
    onEndCallClick: () -> Unit,
    onBlockAndReportSpam: (String, String) -> Unit = { _, _ -> },
    onCallAgainClick: ((String) -> Unit)? = null,
    onDismissEndedScreen: (() -> Unit)? = null,
    onUpdateBackgroundTheme: ((String) -> Unit)? = null,
    onUpdateCustomBackgroundPhoto: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isIncoming = callState == Call.STATE_RINGING
    val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING
    val isEnded = callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING
    val isSlideIncomingStyle = incomingCallLayoutStyle.equals("SLIDE_TO_ANSWER", ignoreCase = true)

    val isVideoUpgradePending by CallManager.isVideoUpgradePending.collectAsState()

    // Switch to full-screen WhatsApp/Snapchat Carrier Video UI when video mode or pending video upgrade is active
    if (isVideoCall || isVideoUpgradePending) {
        CarrierVideoCallScreen(
            callState = callState,
            callerName = callerName,
            callerNumber = callerNumber,
            callerPhotoUri = callerPhotoUri,
            simLabel = simLabel,
            callDurationText = callDurationText,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isFrontCamera = isFrontCamera,
            isVideoUpgradePending = isVideoUpgradePending,
            onBindRemoteSurface = onBindRemoteSurface,
            onBindPreviewSurface = onBindPreviewSurface,
            onAnswerVideoClick = onAnswerClick,
            onAnswerAudioOnlyClick = onAnswerClick,
            onDeclineClick = onDeclineClick,
            onFlipCamera = onFlipCamera,
            onMuteToggle = onMuteToggle,
            onSpeakerToggle = onSpeakerToggle,
            onSwitchToVoice = onVideoToggle,
            onCancelVideoUpgrade = { CallManager.cancelVideoUpgradeRequest() },
            onEndCallClick = onEndCallClick,
            onCallAgainClick = { onCallAgainClick?.invoke(callerNumber.ifBlank { callerName }) },
            onDismissEndedScreen = { onDismissEndedScreen?.invoke() },
        )
        return
    }

    var showDtmfKeypad by remember { mutableStateOf(false) }
    var dtmfSequence by remember { mutableStateOf("") }
    var showQuickReplySheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCallerInfoDialog by remember { mutableStateOf(false) }
    var showBgPickerSheet by remember { mutableStateOf(false) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(callState) {
        if (isEnded) {
            isScreenLocked = false
        }
    }

    var activeThemeId by remember(backgroundTheme) { mutableStateOf(backgroundTheme.ifBlank { "GRAD_AUTO_AVATAR" }) }
    var activeCustomUri by remember(customCallBackgroundUri) { mutableStateOf(customCallBackgroundUri) }

    var finalCallDuration by remember { mutableStateOf("00:00") }
    var wasRecordedDuringCall by remember { mutableStateOf(false) }

    val canUpgradeToVideo by CallManager.canUpgradeToVideo.collectAsState()
    val videoDowngradedNotice by CallManager.videoDowngradedNotice.collectAsState()

    var showBlockSpamDialog by remember { mutableStateOf(false) }

    LaunchedEffect(videoDowngradedNotice) {
        val notice = videoDowngradedNotice
        if (!notice.isNullOrBlank()) {
            context.showToast(notice, Toast.LENGTH_LONG)
            CallManager.clearVideoDowngradedNotice()
        }
    }

    val isVideoEnabled = canUpgradeToVideo && callState == Call.STATE_ACTIVE
    val videoButtonLabel = if (canUpgradeToVideo) "Video call" else "Unavailable"

    LaunchedEffect(callDurationText, isRecording) {
        if (callDurationText != "00:00") {
            finalCallDuration = callDurationText
        }
        if (isRecording) {
            wasRecordedDuringCall = true
        }
    }

    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(callState) {
        if (isDialing) {
            while (true) {
                delay(450L)
                dotCount = (dotCount % 3) + 1
            }
        }
    }

    val resolvedBackgroundColors by CallBackgroundEngine.rememberResolvedCallBackgroundColors(
        themeIdOrTitle = activeThemeId,
        callerPhotoUri = callerPhotoUri,
        callerName = callerName,
        callerNumber = callerNumber,
        isEnded = isEnded,
    )

    val displayTitle = if (isConferenceCall) {
        "Conference call"
    } else {
        callerName.takeIf { it.isNotBlank() && it != callerNumber }
            ?: callerNumber.ifBlank { "Unknown Caller" }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D12)),
    ) {
        val fullScreenPhoto = callerPhotoUri?.takeIf { it.isNotBlank() }
            ?: customCallBackgroundUri?.takeIf { it.isNotBlank() }

        // Clean Ambient Vertical Gradient Background
        val topColor = resolvedBackgroundColors.firstOrNull()?.copy(alpha = 0.65f) ?: Color(0xFF1E293B)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            topColor,
                            Color(0xFF0F172A),
                            Color(0xFF07090E),
                        ),
                    ),
                ),
        )

        // Ergonomic Glassmorphic Contact Photo Container with Dynamic Readjustment & Gradient Border
        if (fullScreenPhoto != null) {
            val topPadding = 160.dp
            val bottomPadding = if (isIncoming) 160.dp else 330.dp

            val glassGradientBorder = remember {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.10f),
                        Color(0xFF10B981).copy(alpha = 0.35f),
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = topPadding, bottom = bottomPadding, start = 28.dp, end = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.5.dp, glassGradientBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (isIncoming) 0.85f else 0.70f),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fullScreenPhoto)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Contact Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(26.dp)),
                    )
                }
            }
        }

        // =========================================================================
        // STAGE 1: IPHONE-STYLE MINIMALIST INCOMING CALL SCREEN (BEFORE RECEIVING)
        // =========================================================================
        if (isIncoming) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top Row: Subtle Left Audio/Announcement Icon & Right Info (i) Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Silence Ringer",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCallerInfoDialog = !showCallerInfoDialog
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Caller Info",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                AppleCallHeader(
                    callerName = displayTitle,
                    callerNumber = callerNumber,
                    statusOrDurationText = "Incoming Call",
                    simLabel = simLabel,
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isSlideIncomingStyle) {
                    // Dual Secondary Action Buttons: "Message" (left) & "Voicemail / Decline" (right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IncomingMiniActionButton(
                            icon = Icons.Default.ChatBubble,
                            label = "Message",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showQuickReplySheet = true
                            },
                        )

                        IncomingMiniActionButton(
                            icon = Icons.Default.Voicemail,
                            label = "Voicemail",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeclineClick()
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Interactive iPhone "slide to answer" pill track
                    SlideToAnswerTrack(
                        onAnswered = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 22.dp),
                    )
                } else {
                    // One UI Dual-Buttons Incoming Action Dock (Decline & Answer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onDeclineClick,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Decline", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAnswerClick,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Answer",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Answer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // STAGES 2, 3 & 4: DIALING, ACTIVE CALL & POST-CALL SUMMARY
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!isEnded && !isIncoming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isScreenLocked = !isScreenLocked
                        }) {
                            Icon(
                                imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isScreenLocked) "Unlock screen" else "Lock screen touch protection",
                                tint = if (isScreenLocked) Color(0xFF10B981) else Color.White,
                            )
                        }

                        IconButton(onClick = { showBgPickerSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Customize background",
                                tint = Color.White,
                            )
                        }

                        Box(contentAlignment = Alignment.Center) {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White,
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(Color(0xFF25272C)),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isScreenLocked) "Unlock screen" else "Lock screen touch protection",
                                            color = if (isScreenLocked) Color(0xFF10B981) else Color.White,
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isScreenLocked = !isScreenLocked
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isRecording) "Stop Recording" else "Record Call",
                                            color = if (isRecording) Color(0xFFEF4444) else Color.White,
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onRecordToggle()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Transfer recording", color = Color.White) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onTransferRecordingClick()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Block & report spam", color = Color(0xFFEF4444)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showBlockSpamDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Call details", color = Color.White) },
                                    onClick = { showOverflowMenu = false },
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (incomingVideoUpgradeRequest && !isEnded) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Caller requested video call",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    onClick = { onRespondVideoUpgrade(false) },
                                    shape = CircleShape,
                                    color = Color(0xFFEF4444),
                                ) {
                                    Text(
                                        text = "Decline",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                                Surface(
                                    onClick = { onRespondVideoUpgrade(true) },
                                    shape = CircleShape,
                                    color = Color(0xFF10B981),
                                ) {
                                    Text(
                                        text = "Accept",
                                        color = Color.Black,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                val activeStatusOrDurationText = when {
                    isEnded -> if (finalCallDuration != "00:00") "Total duration • $finalCallDuration" else "Call ended"
                    isDialing -> "Calling" + ".".repeat(dotCount)
                    callState == Call.STATE_HOLDING -> "Call on hold • $callDurationText"
                    else -> if (callDurationText.isNotBlank()) callDurationText else "Connected"
                }

                AppleCallHeader(
                    callerName = displayTitle,
                    callerNumber = callerNumber,
                    statusOrDurationText = activeStatusOrDurationText,
                    simLabel = simLabel,
                )

                // Center Space / Inline DTMF Keypad
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showDtmfKeypad) {
                        // Inline DTMF Keypad Focus Block
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xFF1E2024).copy(alpha = 0.96f),
                            border = BorderStroke(1.dp, Color(0xFF2E3138)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = dtmfSequence.ifEmpty { "Keypad" },
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { showDtmfKeypad = false }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF94A3B8))
                                    }
                                }
                                HorizontalDivider(
                                    color = Color(0xFF2E3138),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                val dtmfRows = listOf(
                                    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
                                    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
                                    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
                                    listOf('*' to "", '0' to "+", '#' to ""),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    dtmfRows.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                        ) {
                                            rowItems.forEach { (digit, sub) ->
                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        dtmfSequence += digit
                                                        onDtmfDigitPress(digit)
                                                    },
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color(0xFF2A2D35),
                                                    modifier = Modifier
                                                        .width(76.dp)
                                                        .height(48.dp),
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                    ) {
                                                        Text(
                                                            text = digit.toString(),
                                                            fontSize = 19.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                        )
                                                        if (sub.isNotEmpty()) {
                                                            Text(sub, fontSize = 8.sp, color = Color(0xFF94A3B8))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Controls inside the standard Column:
                // 1) Dual-Buttons Incoming Style, 2) Post-Call Summary, OR 3) Active 2x3 Grid
                when {
                    isIncoming -> {
                        // Centered Quick Reply Pill above Red Decline & Green Answer Deck
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showQuickReplySheet = true
                            },
                            shape = CircleShape,
                            color = Color(0xFF1E2024).copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Quick reply",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(36.dp),
                            color = Color(0xFF1E2024).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFF2E3138)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeclineClick()
                                        },
                                        shape = CircleShape,
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.size(72.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = "Decline",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Decline",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onAnswerClick()
                                        },
                                        shape = CircleShape,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.size(72.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Answer",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Answer",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    isEnded -> {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF1E2024).copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, Color(0xFF2E3138)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Quick actions", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                PostCallActionPod(
                                    icon = Icons.Default.Call,
                                    label = "Call again",
                                    bgColor = Color(0xFF10B981),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCallAgainClick?.invoke(callerNumber)
                                    },
                                )
                                PostCallActionPod(
                                    icon = Icons.Default.ChatBubbleOutline,
                                    label = "Message",
                                    bgColor = Color(0xFF38BDF8),
                                    onClick = {
                                        if (callerNumber.isNotBlank()) {
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$callerNumber")).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    },
                                                )
                                            } catch (_: Exception) {
                                            }
                                        }
                                        onDismissEndedScreen?.invoke()
                                    },
                                )
                                PostCallActionPod(
                                    icon = Icons.Default.Videocam,
                                    label = "Video call",
                                    bgColor = Color(0xFF8B5CF6),
                                    onClick = {
                                        if (callerNumber.isNotBlank()) {
                                            com.aether.call.data.CallLogRepository.placeCarrierVideoCall(context, callerNumber)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        onClick = { onDismissEndedScreen?.invoke() },
                        shape = CircleShape,
                        color = Color(0xFF2A2D35),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Close summary", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    }

                    else -> {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF1E2024).copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Color(0xFF2E3138)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OneUiFocusGridButton(
                                    icon = Icons.Default.Videocam,
                                    label = videoButtonLabel,
                                    isActive = isVideoCall,
                                    activeColor = Color(0xFF10B981),
                                    enabled = isVideoEnabled,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onVideoToggle()
                                    },
                                )
                                if (canMergeCalls) {
                                    OneUiFocusGridButton(
                                        icon = Icons.Default.CallMerge,
                                        label = "Merge calls",
                                        isActive = true,
                                        activeColor = Color(0xFF10B981),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onMergeCalls()
                                        },
                                    )
                                } else {
                                    OneUiFocusGridButton(
                                        icon = Icons.Default.Pause,
                                        label = if (isHoldOn) "Resume" else "Hold",
                                        isActive = isHoldOn,
                                        activeColor = Color(0xFFFBBF24),
                                        enabled = !isDialing,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onHoldToggle()
                                        },
                                    )
                                }

                                if (canSwapCalls) {
                                    OneUiFocusGridButton(
                                        icon = Icons.Default.SwapCalls,
                                        label = "Swap",
                                        isActive = true,
                                        activeColor = Color(0xFFFBBF24),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSwapCalls()
                                        },
                                    )
                                } else {
                                    OneUiFocusGridButton(
                                        icon = Icons.Default.Add,
                                        label = "Add call",
                                        isActive = false,
                                        activeColor = Color(0xFF38BDF8),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    },
                                                )
                                            } catch (_: Exception) {
                                            }
                                        },
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OneUiFocusGridButton(
                                    icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Hearing,
                                    label = if (isSpeakerOn) "Speaker ON" else "Speaker",
                                    isActive = isSpeakerOn,
                                    activeColor = Color(0xFF10B981),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSpeakerToggle()
                                    },
                                )
                                OneUiFocusGridButton(
                                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    label = if (isMuted) "Muted" else "Mute",
                                    isActive = isMuted,
                                    activeColor = Color(0xFFF43F5E),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMuteToggle()
                                    },
                                )
                                OneUiFocusGridButton(
                                    icon = Icons.Default.Dialpad,
                                    label = if (showDtmfKeypad) "Hide keypad" else "Keypad",
                                    isActive = showDtmfKeypad,
                                    activeColor = Color(0xFF38BDF8),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showDtmfKeypad = !showDtmfKeypad
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndCallClick()
                        },
                        shape = CircleShape,
                        color = Color(0xFFEF4444),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("End call", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Transparent Touch Blocker when screen is locked (NO dark/blur overlay or text popups)
        if (isScreenLocked && !isEnded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
            )
        }
    }

    if (showQuickReplySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickReplySheet = false },
            containerColor = Color(0xFF1E2024),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Respond with Message",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    quickReplies.forEach { template ->
                        item {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSendQuickReply(template)
                                    showQuickReplySheet = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF141619),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text(template, color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showBgPickerSheet) {
        OneUiCallBackgroundPickerSheet(
            selectedThemeId = activeThemeId,
            customBackgroundUri = activeCustomUri,
            onSelectTheme = { newTheme ->
                activeThemeId = newTheme
                onUpdateBackgroundTheme?.invoke(newTheme)
            },
            onSelectCustomPhoto = { newUri ->
                activeCustomUri = newUri
                onUpdateCustomBackgroundPhoto?.invoke(newUri)
                if (!newUri.isNullOrBlank()) {
                    activeThemeId = "CUSTOM_PHOTO"
                    onUpdateBackgroundTheme?.invoke("CUSTOM_PHOTO")
                }
            },
            onDismiss = { showBgPickerSheet = false },
        )
    }

    if (showBlockSpamDialog) {
        val spamReasons = listOf("Spam / Telemarketer", "Robocall", "Scam / Fraud", "Personal / Unwanted")
        var selectedReason by remember { mutableStateOf(spamReasons.first()) }

        AlertDialog(
            onDismissRequest = { showBlockSpamDialog = false },
            containerColor = Color(0xFF1E2024),
            title = {
                Text("Block & Report Spam", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Block $callerNumber and report to Aether Spam Protection Shield?",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    spamReasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedReason = reason }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(reason, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockSpamDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBlockAndReportSpam(callerNumber, selectedReason)
                    },
                ) {
                    Text("Block & End Call", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockSpamDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
        )
    }
}
}

/**
 * Circular dark translucent button ("Message" / "Voicemail") positioned above the Slide to Answer track.
 */
@Composable
private fun IncomingMiniActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color(0xFF1C1C1E).copy(alpha = 0.90f),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.size(54.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Interactive iOS-style "slide to answer" pill with a white circular green-phone thumb
 * and an animated left-to-right shimmer label.
 */
@Composable
private fun SlideToAnswerTrack(
    onAnswered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val trackHeight = 84.dp
    val thumbSize = 72.dp
    val trackPadding = 6.dp

    val infiniteTransition = rememberInfiniteTransition(label = "SlideShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -250f,
        targetValue = 750f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ShimmerTranslate",
    )

    BoxWithConstraints(
        modifier = modifier
            .height(trackHeight)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E).copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = CircleShape,
            )
            .padding(trackPadding),
        content = {
            val maxDragPx = with(density) {
                (maxWidth - thumbSize).toPx().coerceAtLeast(1f)
            }
        val dragOffset = remember { Animatable(0f) }
        var hasTriggeredAnswer by remember { mutableStateOf(false) }

        val progress = (dragOffset.value / maxDragPx).coerceIn(0f, 1f)

        // Centered "slide to answer" shimmer text that fades out as the user drags the thumb
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = thumbSize / 2),
            contentAlignment = Alignment.Center,
        ) {
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.32f * (1f - progress)).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = (0.95f * (1f - progress)).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = (0.32f * (1f - progress)).coerceIn(0f, 1f)),
                ),
                start = Offset(shimmerOffset, 0f),
                end = Offset(shimmerOffset + 220f, 0f),
                tileMode = TileMode.Clamp,
            )
            Text(
                text = "slide to answer",
                style = TextStyle(
                    brush = shimmerBrush,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
            )
        }

        // Draggable White Circular Thumb with Green Phone Icon
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(thumbSize)
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (!hasTriggeredAnswer) {
                            val nextVal = (dragOffset.value + delta).coerceIn(0f, maxDragPx)
                            coroutineScope.launch {
                                dragOffset.snapTo(nextVal)
                            }
                        }
                    },
                    onDragStarted = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragStopped = {
                        if (hasTriggeredAnswer) return@draggable
                        if (dragOffset.value >= maxDragPx * 0.78f) {
                            hasTriggeredAnswer = true
                            coroutineScope.launch {
                                dragOffset.animateTo(maxDragPx, tween(110))
                                onAnswered()
                            }
                        } else {
                            coroutineScope.launch {
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        }
                    },
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Slide to answer",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    })
}

@Composable
private fun OneUiFocusGridButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val buttonWidth by animateDpAsState(
        targetValue = if (isActive) 68.dp else 56.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ButtonWidthSpring",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(88.dp),
    ) {
        Surface(
            onClick = {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            },
            shape = if (isActive) RoundedCornerShape(20.dp) else CircleShape,
            color = when {
                !enabled -> Color(0xFF22252B)
                isActive -> activeColor
                else -> Color(0xFF2A2D35)
            },
            modifier = Modifier
                .width(buttonWidth)
                .height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = when {
                        !enabled -> Color(0xFF52525B)
                        isActive -> Color.Black
                        else -> Color.White
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> Color(0xFF52525B)
                isActive -> activeColor
                else -> Color(0xFFE2E8F0)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PostCallActionPod(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = onClick, shape = CircleShape, color = bgColor, modifier = Modifier.size(54.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AppleCallHeader(
    callerName: String,
    callerNumber: String,
    statusOrDurationText: String,
    simLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 52.dp, start = 24.dp, end = 24.dp),
    ) {
        // Call Status / Timer at top (Apple iOS 18 style)
        Text(
            text = statusOrDurationText,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Large Apple Poster Display Name
        Text(
            text = callerName,
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 42.sp,
        )

        // Phone Number subtitle (shown when callerName is a saved contact name)
        if (callerNumber.isNotBlank() && callerNumber != callerName) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = callerNumber,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }

        // Optional subtle SIM badge
        if (simLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
            ) {
                Text(
                    text = simLabel,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}