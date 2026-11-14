package com.aether.call.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlphabetScrubber(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val letters = remember {
        listOf('★') + ('A'..'Z').toList() + listOf('#')
    }

    Column(
        modifier = modifier
            .padding(vertical = 12.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val y = change.position.y
                    val totalHeight = size.height.toFloat()
                    if (totalHeight > 0f) {
                        val fraction = (y / totalHeight).coerceIn(0f, 0.999f)
                        val index = (fraction * letters.size).toInt()
                        if (index in letters.indices) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letters[index])
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { char ->
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93),
                ),
            )
        }
    }
}
