package com.aether.call.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Samsung One UI Floating Heads-Up Call Card overlay.
 */
@Composable
fun OneUiFloatingCallBanner(
    callerName: String,
    callerNumber: String,
    simLabel: String = "IND-JIO",
    onExpandToFullScreen: () -> Unit,
    onDeclineClick: () -> Unit,
    onAnswerClick: () -> Unit,
    onQuickReplyClick: () -> Unit,
    onSilenceRinger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF1E2024),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFF2E3138)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount > 24f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExpandToFullScreen()
                    } else if (dragAmount < -24f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSilenceRinger()
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // TOP ROW
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val initial = callerName.trimStart().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFF2A2D35), CircleShape)
                        .clip(CircleShape),
                ) {
                    Text(
                        text = initial,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callerName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (callerNumber.isNotBlank() && callerName != callerNumber) "$callerNumber • via " else "via ",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                        )
                        Text(
                            text = simLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF43F5E),
                        )
                    }

                    Text(
                        text = "Swipe down to expand • Swipe up to silence",
                        fontSize = 11.sp,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ACTION ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // DECLINE POD
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeclineClick()
                        },
                        shape = CircleShape,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Text(
                        text = "Decline",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // ANSWER POD
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerClick()
                        },
                        shape = CircleShape,
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Answer",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Text(
                        text = "Answer",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // QUICK REPLY BUTTON
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQuickReplyClick()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quick reply",
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
