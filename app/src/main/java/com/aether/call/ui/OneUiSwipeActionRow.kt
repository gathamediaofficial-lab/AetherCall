package com.aether.call.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun OneUiSwipeActionRow(
    phoneNumber: String,
    onSwipeCall: (String) -> Unit,
    onSwipeMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val triggerThresholdPx = remember(density) { with(density) { 84.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 126.dp.toPx() } }

    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var hapticTriggered by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "oneUiSwipe"
    )

    val progress = (abs(animatedOffsetX) / triggerThresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (phoneNumber.isNotBlank()) {
                    Modifier.pointerInput(phoneNumber) {
                        detectHorizontalDragGestures(
                            onDragStart = { hapticTriggered = false },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val updated = (rawOffsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                                rawOffsetX = updated
                                if (abs(updated) >= triggerThresholdPx && !hapticTriggered) {
                                    hapticTriggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (abs(updated) < triggerThresholdPx) {
                                    hapticTriggered = false
                                }
                            },
                            onDragEnd = {
                                val finalX = rawOffsetX
                                rawOffsetX = 0f
                                hapticTriggered = false
                                when {
                                    finalX >= triggerThresholdPx -> onSwipeCall(phoneNumber)
                                    finalX <= -triggerThresholdPx -> onSwipeMessage(phoneNumber)
                                }
                            },
                            onDragCancel = {
                                rawOffsetX = 0f
                                hapticTriggered = false
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        if (animatedOffsetX > 2f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF10B981).copy(alpha = 0.25f + 0.75f * progress))
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            val s = 0.85f + 0.15f * progress
                            scaleX = s
                            scaleY = s
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (animatedOffsetX < -2f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF3B82F6).copy(alpha = 0.25f + 0.75f * progress))
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text("Message", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Message",
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            val s = 0.85f + 0.15f * progress
                            scaleX = s
                            scaleY = s
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
        ) {
            content()
        }
    }
}