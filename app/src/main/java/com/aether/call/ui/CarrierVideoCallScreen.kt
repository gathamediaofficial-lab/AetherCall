package com.aether.call.ui

import android.telecom.Call
import android.view.Surface
import android.view.TextureView
import android.graphics.SurfaceTexture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    onBindRemoteSurface: (Surface?) -> Unit,
    onBindPreviewSurface: (Surface?) -> Unit,
    onAnswerVideoClick: () -> Unit,
    onAnswerAudioOnlyClick: () -> Unit,
    onDeclineClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onEndCallClick: () -> Unit,
    onCallAgainClick: () -> Unit,
    onDismissEndedScreen: () -> Unit,
) {
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var finalCallDuration by remember { mutableStateOf("00:00") }

    LaunchedEffect(callDurationText) {
        if (callDurationText != "00:00") {
            finalCallDuration = callDurationText
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .clickable(enabled = callState == Call.STATE_ACTIVE) {
                controlsVisible = !controlsVisible
            }
    ) {
        when (callState) {
            // =========================================================================
            // PAGE 1: INCOMING VIDEO CALL SCREEN (Ringing)
            // =========================================================================
            Call.STATE_RINGING -> {
                // Full-screen local camera preview or full-screen saved photo background
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    onBindPreviewSurface(Surface(st))
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    onBindPreviewSurface(null)
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (!callerPhotoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(callerPhotoUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Apple-style subtle top & bottom dark gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Top iOS-style Caller Identity (No circular avatar — pure Apple Poster typography)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = 24.dp, end = 24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (simLabel.isNotBlank()) "$simLabel • Incoming Video Call" else "Incoming Carrier Video Call",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = callerName,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (callerNumber.isNotBlank() && callerNumber != callerName) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = callerNumber,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Bottom Incoming Video Call Action Dock: Decline | Answer Audio | Accept Video
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 48.dp)
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDeclineClick,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Decline", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    // Answer as Voice Only Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAnswerAudioOnlyClick,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Answer as Audio",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Voice Only", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }

                    // Accept Video Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAnswerVideoClick,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Accept Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Accept Video", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // =========================================================================
            // PAGE 3: END VIDEO CALL SCREEN (Disconnecting / Disconnected)
            // =========================================================================
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> {
                if (!callerPhotoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(callerPhotoUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f))
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 56.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Video Call Ended",
                            color = Color(0xFFEF4444),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = callerName,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (callerNumber.isNotBlank() && callerNumber != callerName) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = callerNumber,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (finalCallDuration != "00:00") "Total duration • $finalCallDuration" else "Video Call Ended",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onCallAgainClick,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
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
                                    .background(Color.White.copy(alpha = 0.18f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Done", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Done", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // =========================================================================
            // PAGE 2: ACTIVE / DIALING VIDEO CALL SCREEN
            // =========================================================================
            else -> {
                // Full-screen Remote Peer Video Surface
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    onBindRemoteSurface(Surface(st))
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    onBindRemoteSurface(null)
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left Apple FaceTime-style frosted status pill
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = callerName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (callState == Call.STATE_ACTIVE) callDurationText else "Connecting Video...",
                                color = Color(0xFF6EE7B7),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Draggable Self-View PiP Preview Card (Top-Right)
                Box(
                    modifier = Modifier
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
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        onBindPreviewSurface(Surface(st))
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        onBindPreviewSurface(null)
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom Frosted Control Dock
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                        ) {
                            IconButton(
                                onClick = onFlipCamera,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = Color.White)
                            }

                            IconButton(
                                onClick = onMuteToggle,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (isMuted) Color.White else Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) Color.Black else Color.White
                                )
                            }

                            IconButton(
                                onClick = onSpeakerToggle,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speaker",
                                    tint = if (isSpeakerOn) Color.Black else Color.White
                                )
                            }

                            IconButton(
                                onClick = onSwitchToVoice,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Default.VideocamOff, contentDescription = "Voice Only", tint = Color.White)
                            }

                            IconButton(
                                onClick = onEndCallClick,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
