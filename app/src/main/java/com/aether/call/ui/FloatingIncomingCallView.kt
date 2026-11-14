package com.aether.call.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FloatingIncomingCallView(
    callerName: String,
    callerNumber: String,
    simLabel: String = "IND-JIO",
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
    val coroutineScope = rememberCoroutineScope()
    val initials = callerName.trimStart().take(2).uppercase(Locale.getDefault())

    val offsetY = remember { Animatable(0f) }
    val swipeThreshold = 140f
    var showQuickReplies by remember { mutableStateOf(false) }

    val defaultReplies = remember {
        listOf(
            "Can't talk now",
            "Call you right back",
            "In a meeting",
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            offsetY.snapTo(offsetY.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        val currentY = offsetY.value
                        if (currentY > swipeThreshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExpandFullScreen()
                        } else if (currentY < -swipeThreshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSilence()
                            coroutineScope.launch {
                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        } else {
                            coroutineScope.launch {
                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetY.animateTo(0f)
                        }
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandFullScreen() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A)),
                            ),
                            shape = CircleShape,
                        )
                        .border(1.5.dp, Color(0x6660A5FA), CircleShape)
                        .clip(CircleShape),
                ) {
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
                            text = initials.ifEmpty { "?" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callerName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (callerNumber.isNotBlank() && callerName != callerNumber) "$callerNumber • via " else "via ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = simLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = Color(0xFFF43F5E),
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Swipe down to expand • Swipe up to silence",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDecline()
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                            .border(1.5.dp, Color(0xFFFCA5A5), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = "Decline",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswer()
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .background(Color(0xFF10B981), CircleShape)
                            .border(1.5.dp, Color(0xFFA7F3D0), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = "Answer",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showQuickReplies = !showQuickReplies
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Quick reply",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            AnimatedVisibility(
                visible = showQuickReplies,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    items(defaultReplies) { text ->
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQuickReply(text)
                            },
                            label = { Text(text, color = Color.White, fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2024)),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                        )
                    }
                }
            }
        }
    }
}
