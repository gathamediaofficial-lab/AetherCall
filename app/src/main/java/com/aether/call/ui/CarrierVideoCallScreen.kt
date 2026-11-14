package com.aether.call.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@Composable
fun CarrierVideoCallScreen(
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
    onFlipCamera: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onEndCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Tap anywhere on the video canvas to toggle HUD visibility (Snapchat/WhatsApp style)
    var controlsVisible by remember { mutableStateOf(true) }
    var remoteSurfaceReady by remember { mutableStateOf(false) }

    // Draggable offset for the top-right Picture-in-Picture (PiP) self-view card
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }

    val displayTitle = callerName.takeIf { it.isNotBlank() && it != callerNumber }
        ?: callerNumber.ifBlank { "Unknown Caller" }

    DisposableEffect(Unit) {
        onDispose {
            onBindRemoteSurface(null)
            onBindPreviewSurface(null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controlsVisible = !controlsVisible
            },
    ) {
        // =========================================================================
        // 1. FULL-SCREEN REMOTE CARRIER VIDEO SURFACE (IMS / ViLTE Display Surface)
        // =========================================================================
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var activeSurface: Surface? = null

                        override fun onSurfaceTextureAvailable(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            activeSurface = Surface(st)
                            remoteSurfaceReady = true
                            onBindRemoteSurface(activeSurface)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            remoteSurfaceReady = false
                            onBindRemoteSurface(null)
                            activeSurface?.release()
                            activeSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Subtle ambient avatar/gradient fallback while carrier video stream is negotiating
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )

        // =========================================================================
        // 2. TOP-LEFT FROSTED CALLER INFO PILL (Matches Approved Mockup)
        // =========================================================================
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(start = 18.dp, top = 14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF2B2F36).copy(alpha = 0.76f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = callDurationText,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "• HD $simLabel",
                                color = Color(0xFF6EE7B7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Quick Speaker / Earpiece Route Toggle inside Header Pill
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeakerToggle()
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = if (isSpeakerOn) 0.24f else 0.10f),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Hearing,
                                contentDescription = "Toggle Speaker",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. TOP-RIGHT DRAGGABLE PICTURE-IN-PICTURE (PiP) SELF-CAMERA PREVIEW
        // =========================================================================
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 14.dp, end = 18.dp)
                .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                .size(width = 106.dp, height = 156.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E222B))
                .border(
                    width = 1.2.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp),
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pipOffsetX += dragAmount.x
                        pipOffsetY += dragAmount.y
                    }
                }
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onFlipCamera()
                },
        ) {
            // Local Camera Preview Surface (Bound to Telecom VideoCall.setPreviewSurface)
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            private var previewSurface: Surface? = null

                            override fun onSurfaceTextureAvailable(
                                st: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                previewSurface = Surface(st)
                                onBindPreviewSurface(previewSurface)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                st: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                onBindPreviewSurface(null)
                                previewSurface?.release()
                                previewSurface = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Subtle camera indicator badge in bottom-right of PiP
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.50f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = if (isFrontCamera) "Front Camera" else "Back Camera",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // =========================================================================
        // 4. BOTTOM FROSTED GLASS CONTROL DOCK (Flip, Mute, Voice, End)
        // =========================================================================
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF2B2F36).copy(alpha = 0.80f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 18.dp, start = 12.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Subtle frosted drag handle bar at top of dock (matches mockup)
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.30f)),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. Flip Camera
                        FrostedVideoDockButton(
                            icon = Icons.Default.Cameraswitch,
                            label = "Flip",
                            containerColor = Color.White.copy(alpha = 0.65f),
                            iconTint = Color(0xFF111827),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onFlipCamera()
                            },
                        )

                        // 2. Mute / Unmute Audio
                        FrostedVideoDockButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) "Muted" else "Mute",
                            containerColor = if (isMuted) Color(0xFFF43F5E) else Color.White.copy(alpha = 0.65f),
                            iconTint = if (isMuted) Color.White else Color(0xFF111827),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMuteToggle()
                            },
                        )

                        // 3. Switch to Voice Call
                        FrostedVideoDockButton(
                            icon = Icons.Default.PhoneInTalk,
                            label = "Voice",
                            containerColor = Color.White.copy(alpha = 0.82f),
                            iconTint = Color(0xFF111827),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwitchToVoice()
                            },
                        )

                        // 4. End Call
                        FrostedVideoDockButton(
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            containerColor = Color(0xFFEF4444),
                            iconTint = Color.White,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEndCallClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedVideoDockButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(62.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}