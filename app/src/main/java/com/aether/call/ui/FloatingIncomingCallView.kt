package com.aether.call.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aether.call.telecom.CallBridge
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

@Composable
fun FloatingIncomingCallView(
    callerName: String,
    callerNumber: String,
    simLabel: String = "SIM 1 • Jio",
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onQuickReply: (String) -> Unit,
    onExpandFullScreen: () -> Unit,
    onSilence: () -> Unit,
    posterPhotoUri: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val initial = callerName.trimStart().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

    var offsetY by remember { mutableFloatStateOf(0f) }
    var showQuickReplies by remember { mutableStateOf(false) }

    val defaultReplies = remember {
        listOf(
            "Can't talk now",
            "Call you right back",
            "In a meeting",
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF516181D),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    offsetY = (offsetY + delta).coerceAtMost(50f)
                },
                onDragStopped = {
                    if (offsetY < -80f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSilence()
                    }
                    offsetY = 0f
                },
            )
            .clickable { onExpandFullScreen() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // TOP ROW: 44.dp Circular Mini Avatar, Caller Name & Number + SIM, Expand Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 44.dp Circular Mini Avatar
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF262930),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!posterPhotoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(posterPhotoUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = callerName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = initial,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Caller Name & Number / SIM Badge
                Column(modifier = Modifier.weight(1f)) {
                    val isCnapVerified by CallBridge.isCnapVerified.collectAsState()
                    Text(
                        text = callerName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (callerNumber.isNotBlank() && callerName != callerNumber) {
                            if (isCnapVerified) "$callerNumber • Verified CNAP" else "$callerNumber • $simLabel"
                        } else {
                            simLabel
                        },
                        color = if (isCnapVerified) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Expand Full-Screen Button
                IconButton(
                    onClick = onExpandFullScreen,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Expand to full screen",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM ROW: Balanced 44.dp-tall Pill Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Decline Pill Button (#EF4444)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDecline()
                    },
                    shape = CircleShape,
                    color = Color(0xFFEF4444),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Decline",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Quick Message Icon Button (#262930)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showQuickReplies = !showQuickReplies
                    },
                    shape = CircleShape,
                    color = Color(0xFF262930),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Quick Reply",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Answer Pill Button (#10B981)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnswer()
                    },
                    shape = CircleShape,
                    color = Color(0xFF10B981),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Answer",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showQuickReplies,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(defaultReplies) { replyText ->
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQuickReply(replyText)
                            },
                            label = { Text(replyText, color = Color.White, fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF262930)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        )
                    }
                }
            }
        }
    }
}
