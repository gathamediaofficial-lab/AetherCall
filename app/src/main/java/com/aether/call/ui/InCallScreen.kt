package com.aether.call.ui

import android.content.Intent
import android.net.Uri
import android.telecom.Call
import android.view.Surface
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Videocam
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
    onEndCallClick: () -> Unit,
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

    // Switch to full-screen WhatsApp/Snapchat Carrier Video UI when video mode is active
    if (isVideoCall && !isIncoming && !isEnded) {
        CarrierVideoCallScreen(
            callerName = callerName,
            callerNumber = callerNumber,
            callerPhotoUri = callerPhotoUri,
            simLabel = simLabel,
            callDurationText = callDurationText,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isFrontCamera = isFrontCamera,
            onBindRemoteSurface = onBindRemoteSurface,
            onBindPreviewSurface = onBindPreviewSurface,
            onFlipCamera = onFlipCamera,
            onMuteToggle = onMuteToggle,
            onSpeakerToggle = onSpeakerToggle,
            onSwitchToVoice = onVideoToggle,
            onEndCallClick = onEndCallClick,
            modifier = modifier,
        )
        return
    }

    var showDtmfKeypad by remember { mutableStateOf(false) }
    var dtmfSequence by remember { mutableStateOf("") }
    var showQuickReplySheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCallerInfoDialog by remember { mutableStateOf(false) }
    var showBgPickerSheet by remember { mutableStateOf(false) }

    var activeThemeId by remember(backgroundTheme) { mutableStateOf(backgroundTheme.ifBlank { "GRAD_AUTO_AVATAR" }) }
    var activeCustomUri by remember(customCallBackgroundUri) { mutableStateOf(customCallBackgroundUri) }

    var finalCallDuration by remember { mutableStateOf("00:00") }
    var wasRecordedDuringCall by remember { mutableStateOf(false) }

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

    val displayTitle = callerName.takeIf { it.isNotBlank() && it != callerNumber }
        ?: callerNumber.ifBlank { "Unknown Caller" }

    // For incoming calls, use the deep minimalist iOS dark canvas while keeping subtle theme tinting
    val activeGradientColors = if (isIncoming && activeThemeId == "SOLID_AMOLED") {
        listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000))
    } else if (isIncoming) {
        listOf(
            resolvedBackgroundColors.first().copy(alpha = 0.55f),
            Color(0xFF07080A),
            Color(0xFF000000),
        )
    } else {
        resolvedBackgroundColors
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(brush = Brush.verticalGradient(colors = activeGradientColors))
            .systemBarsPadding(),
    ) {
        if (!activeCustomUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(activeCustomUri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (isIncoming) 0.65f else 0.50f),
                                Color.Black.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )
        }

        // =========================================================================
        // STAGE 1: IPHONE-STYLE MINIMALIST INCOMING CALL SCREEN (BEFORE RECEIVING)
        // =========================================================================
        if (isIncoming && isSlideIncomingStyle) {
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

                Spacer(modifier = Modifier.height(18.dp))

                // Small "mobile" label above caller name (exact iPhone hierarchy)
                Text(
                    text = if (simLabel.isNotBlank()) "mobile • $simLabel" else "mobile",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Large Bold Caller Name ("Samuel" / "Mr Naresh Jio")
                Text(
                    text = displayTitle,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp,
                )

                // Optional secondary phone number line if contact has a saved name
                if (callerNumber.isNotBlank() && callerName != callerNumber && showCallerInfoDialog) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = callerNumber,
                        fontSize = 15.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Expansive minimalist center space (or optional subtle contact photo if set)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!callerPhotoUri.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.size(116.dp),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(callerPhotoUri).crossfade(true).build(),
                                contentDescription = displayTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

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

                // SIM & Status Badge
                Surface(
                    shape = CircleShape,
                    color = if (isEnded) {
                        Color(0xFFEF4444).copy(alpha = 0.22f)
                    } else {
                        Color(0xFF10B981).copy(alpha = 0.20f)
                    },
                    border = BorderStroke(
                        0.8.dp,
                        if (isEnded) {
                            Color(0xFFEF4444).copy(alpha = 0.45f)
                        } else {
                            Color(0xFF10B981).copy(alpha = 0.45f)
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                isEnded -> "Call ended • $simLabel"
                                isDialing -> "Calling via $simLabel"
                                else -> "HD Voice • $simLabel"
                            },
                            color = if (isEnded) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = displayTitle,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )

                if (callerNumber.isNotBlank() && callerName != callerNumber) {
                    Text(
                        text = "Mobile • $callerNumber",
                        fontSize = 15.sp,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = callState,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                    label = "StageSubtitle",
                ) { state ->
                    when (state) {
                        Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                            Text(
                                text = "Calling" + ".".repeat(dotCount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                            )
                        }
                        Call.STATE_HOLDING -> {
                            Surface(shape = CircleShape, color = Color(0xFFFBBF24).copy(alpha = 0.2f)) {
                                Text(
                                    text = "⏸ Call on hold • $callDurationText",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (finalCallDuration != "00:00") "Total duration • $finalCallDuration" else "Call ended",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF87171),
                                )
                                if (wasRecordedDuringCall) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(shape = CircleShape, color = Color(0xFF10B981).copy(alpha = 0.18f)) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Call recording saved",
                                                color = Color(0xFF10B981),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = callDurationText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                )
                                if (isRecording) {
                                    Surface(shape = CircleShape, color = Color(0xFFEF4444).copy(alpha = 0.22f)) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444)),
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "REC",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Center Avatar & Radar Pulse Hub
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!showDtmfKeypad) {
                        val isPulsing = isDialing || callState == Call.STATE_ACTIVE
                        if (isPulsing) {
                            val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
                            val outerScale by infiniteTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = if (isDialing) 1.42f else 1.18f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart,
                                ),
                                label = "OuterScale",
                            )
                            val outerAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.36f,
                                targetValue = 0.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart,
                                ),
                                label = "OuterAlpha",
                            )
                            Box(
                                modifier = Modifier
                                    .size(144.dp)
                                    .scale(outerScale)
                                    .background(Color(0xFF10B981).copy(alpha = outerAlpha), CircleShape),
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            modifier = Modifier
                                .size(126.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (isEnded) {
                                        Color(0xFFEF4444).copy(alpha = 0.5f)
                                    } else {
                                        Color(0xFF10B981).copy(alpha = 0.65f)
                                    },
                                    shape = CircleShape,
                                ),
                        ) {
                            val avatarAccent = remember(displayTitle, callerNumber) {
                                CallBackgroundEngine.getAvatarAccentColor(displayTitle, callerNumber)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                avatarAccent.copy(alpha = 0.85f),
                                                avatarAccent.copy(alpha = 0.45f),
                                            ),
                                        ),
                                    ),
                            ) {
                                if (!callerPhotoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(callerPhotoUri).crossfade(true).build(),
                                        contentDescription = displayTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    val cleanInitial = CallBackgroundEngine.extractCleanFirstNameLetter(displayTitle, callerNumber)
                                    Text(
                                        text = cleanInitial,
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    } else {
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
                                    onClick = { onCallAgainClick?.invoke(callerNumber) },
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
                                    label = if (isVideoCall) "Voice call" else "Video call",
                                    isActive = isVideoCall,
                                    activeColor = Color(0xFF10B981),
                                    enabled = !isDialing,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onVideoToggle()
                                    },
                                )
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