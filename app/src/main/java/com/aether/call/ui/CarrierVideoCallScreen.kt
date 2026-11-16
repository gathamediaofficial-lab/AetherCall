package com.aether.call.ui

import android.graphics.SurfaceTexture
import android.telecom.Call
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

@Composable
fun CarrierVideoCallScreen(
    callState: Int,
    callerName: String,
    callerNumber: String,
    callerPhotoUri: String?,
    simLabel: String,
    callDurationText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isFrontCamera: Boolean,
    isVideoUpgradePending: Boolean = false,
    onBindRemoteSurface: (Surface?) -> Unit,
    onBindPreviewSurface: (Surface?) -> Unit,
    onAnswerVideoClick: () -> Unit,
    onAnswerAudioOnlyClick: () -> Unit,
    onDeclineClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onCancelVideoUpgrade: () -> Unit = onSwitchToVoice,
    onEndCallClick: () -> Unit,
    onCallAgainClick: () -> Unit,
    onDismissEndedScreen: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var finalCallDuration by remember { mutableStateOf("00:00") }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
            isScreenLocked = false
        }
    }

    // Track exact SurfaceTexture identity so Stage 1 -> Stage 2 handoff NEVER calls
    // onBindPreviewSurface(null) after the new PiP Surface has already bound!
    var activePreviewTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var activePreviewSurface by remember { mutableStateOf<Surface?>(null) }
    var activeRemoteTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var activeRemoteSurface by remember { mutableStateOf<Surface?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            onBindPreviewSurface(null)
            onBindRemoteSurface(null)
            activePreviewSurface?.release()
            activeRemoteSurface?.release()
        }
    }

    LaunchedEffect(callDurationText) {
        if (callDurationText != "00:00") {
            finalCallDuration = callDurationText
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "PulseAlpha",
    )

    val isEndedStage = callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED
    val isStage1Preview = !isEndedStage && (
            callState == Call.STATE_RINGING ||
                    callState == Call.STATE_DIALING ||
                    callState == Call.STATE_CONNECTING ||
                    callState == Call.STATE_SELECT_PHONE_ACCOUNT ||
                    isVideoUpgradePending
            )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .clickable(enabled = callState == Call.STATE_ACTIVE && !isVideoUpgradePending) {
                controlsVisible = !controlsVisible
            },
    ) {
        // =========================================================================
        // 1. REMOTE PEER VIDEO SURFACE (Pre-mounted when active or connecting so IMS
        //    always has a valid display surface ready the millisecond call answers)
        // =========================================================================
        if (!isEndedStage && !isStage1Preview) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                activeRemoteSurface?.release()
                                val surface = Surface(st)
                                activeRemoteTexture = st
                                activeRemoteSurface = surface
                                onBindRemoteSurface(surface)
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                if (activeRemoteTexture === st) {
                                    onBindRemoteSurface(null)
                                    activeRemoteSurface?.release()
                                    activeRemoteSurface = null
                                    activeRemoteTexture = null
                                }
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // =========================================================================
        // 2. SINGLE PERSISTENT LOCAL CAMERA PREVIEW TEXTUREVIEW
        //    Morphs smoothly between Full-Screen (Stage 1) and Draggable PiP (Stage 2)
        //    so the SurfaceTexture is NEVER destroyed when the recipient answers!
        // =========================================================================
        if (!isEndedStage) {
            val previewModifier = if (isStage1Preview) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .size(width = 116.dp, height = 168.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2024))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    }
                    .clickable { onFlipCamera() }
            }

            Box(modifier = previewModifier) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    activePreviewSurface?.release()
                                    val surface = Surface(st)
                                    activePreviewTexture = st
                                    activePreviewSurface = surface
                                    onBindPreviewSurface(surface)
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    if (activePreviewTexture === st) {
                                        onBindPreviewSurface(null)
                                        activePreviewSurface?.release()
                                        activePreviewSurface = null
                                        activePreviewTexture = null
                                    }
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // =========================================================================
        // 3. OVERLAYS & CONTROLS PER STAGE
        // =========================================================================
        if (isStage1Preview) {
            if (!callerPhotoUri.isNullOrBlank() && callState == Call.STATE_RINGING) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(callerPhotoUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Top & Bottom gradient scrims for contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.70f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )

            // Top Status Pill & Caller Identity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp),
            ) {
                val statusText = when {
                    isVideoUpgradePending -> "Requesting video upgrade... Waiting for recipient"
                    callState == Call.STATE_RINGING -> {
                        if (simLabel.isNotBlank()) "$simLabel • Incoming HD Video Call..." else "Incoming HD Video Call..."
                    }
                    callState == Call.STATE_DIALING -> "Calling..."
                    callState == Call.STATE_CONNECTING -> "Connecting video..."
                    else -> "Connecting video..."
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isVideoUpgradePending) {
                                        Color(0xFFFBBF24).copy(alpha = pulseAlpha)
                                    } else {
                                        Color(0xFF10B981).copy(alpha = pulseAlpha)
                                    },
                                ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (callerNumber.isNotBlank() && callerNumber != callerName) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = callerNumber,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            // Bottom Action Dock
            if (isVideoUpgradePending) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onCancelVideoUpgrade,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Request",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Cancel Request", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else if (callState == Call.STATE_RINGING) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
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
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Decline", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAnswerAudioOnlyClick,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Answer as Audio",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Voice Only", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAnswerVideoClick,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Accept Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Accept Video", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onFlipCamera,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Flip Camera",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Flip", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onEndCallClick,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Cancel", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else if (isEndedStage) {
            if (!callerPhotoUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(callerPhotoUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 56.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Video Call Ended",
                        color = Color(0xFFEF4444),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = callerName,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (callerNumber.isNotBlank() && callerNumber != callerName) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = callerNumber,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (finalCallDuration != "00:00") "Total duration • $finalCallDuration" else "Video Call Ended",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onCallAgainClick,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Call Again", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Call Again", color = Color.White, fontSize = 13.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDismissEndedScreen,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Done", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Done", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // =========================================================================
            // STAGE 2 OVERLAYS: ACTIVE CONNECTED VIDEO CALL CONTROLS
            // =========================================================================
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = callerName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = callDurationText,
                            color = Color(0xFF6EE7B7),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                    ) {
                        IconButton(
                            onClick = onFlipCamera,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = Color.White)
                        }

                        IconButton(
                            onClick = onMuteToggle,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color.White else Color.White.copy(alpha = 0.16f)),
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color.Black else Color.White,
                            )
                        }

                        IconButton(
                            onClick = onSpeakerToggle,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.16f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) Color.Black else Color.White,
                            )
                        }

                        IconButton(
                            onClick = onSwitchToVoice,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                        ) {
                            Icon(Icons.Default.VideocamOff, contentDescription = "Voice Only", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isScreenLocked = !isScreenLocked
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isScreenLocked) Color(0xFF10B981) else Color.White.copy(alpha = 0.16f)),
                        ) {
                            Icon(
                                imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isScreenLocked) "Unlock Screen" else "Lock Screen",
                                tint = if (isScreenLocked) Color.Black else Color.White,
                            )
                        }

                        IconButton(
                            onClick = onEndCallClick,
                            enabled = !isScreenLocked,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isScreenLocked) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFFEF4444)),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Transparent Touch Blocker when screen is locked (NO dark/blur overlay or text popups)
        if (isScreenLocked && !isEndedStage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
            )

            // Floating Lock Toggle Pill at Bottom Center when controls are hidden
            AnimatedVisibility(
                visible = !controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isScreenLocked = false
                    },
                    shape = CircleShape,
                    color = Color(0xFF10B981),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock Screen",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}