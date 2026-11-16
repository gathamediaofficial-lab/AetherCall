package com.aether.call.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.call.data.ContactItem
import java.util.Locale

private fun charToT9Digit(c: Char): Char {
    return when (c.lowercaseChar()) {
        'a', 'b', 'c' -> '2'
        'd', 'e', 'f' -> '3'
        'g', 'h', 'i' -> '4'
        'j', 'k', 'l' -> '5'
        'm', 'n', 'o' -> '6'
        'p', 'q', 'r', 's' -> '7'
        't', 'u', 'v' -> '8'
        'w', 'x', 'y', 'z' -> '9'
        else -> c
    }
}

private fun nameToT9Digits(name: String): String {
    val sb = StringBuilder()
    for (ch in name) {
        if (ch.isLetter()) {
            sb.append(charToT9Digit(ch))
        }
    }
    return sb.toString()
}

private fun playKeypadTone(digit: Char, mode: String) {
    if (mode == "Off") return
    try {
        val toneType = if (mode == "Retro") {
            ToneGenerator.TONE_PROP_BEEP
        } else {
            when (digit) {
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '0', '+' -> ToneGenerator.TONE_DTMF_0
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_PROP_BEEP
            }
        }
        val toneGen = ToneGenerator(AudioManager.STREAM_DTMF, 80)
        toneGen.startTone(toneType, 120)
    } catch (_: Exception) {
    }
}

/**
 * Authentic Samsung One UI 3x4 Keypad Screen with Real-Time T9 Search and Reachability Architecture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OneUiKeypadScreen(
    enteredNumber: String,
    allContacts: List<ContactItem>,
    keypadToneMode: String = "Default",
    onDigitPress: (Char) -> Unit,
    onDeletePress: () -> Unit,
    onClearAll: () -> Unit,
    onCallClick: (String) -> Unit,
    onAddToContactClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val t9Matches = remember(enteredNumber, allContacts) {
        if (enteredNumber.isBlank()) {
            emptyList()
        } else {
            val cleanQuery = enteredNumber.replace("\\s+".toRegex(), "")
            allContacts.filter { contact ->
                val cleanNum = contact.number.replace("\\s+".toRegex(), "")
                val t9Name = nameToT9Digits(contact.name)
                cleanNum.contains(cleanQuery) || t9Name.contains(cleanQuery)
            }.take(15)
        }
    }

    val keys = remember {
        listOf(
            listOf(('1' to "o_o"), ('2' to "ABC"), ('3' to "DEF")),
            listOf(('4' to "GHI"), ('5' to "JKL"), ('6' to "MNO")),
            listOf(('7' to "PQRS"), ('8' to "TUV"), ('9' to "WXYZ")),
            listOf(('*' to ""), ('0' to "+"), ('#' to "")),
        )
    }

    val avatarColors = remember {
        listOf(
            Color(0xFFEC4899),
            Color(0xFFF97316),
            Color(0xFF06B6D4),
            Color(0xFF10B981),
            Color(0xFFA855F7),
            Color(0xFF3B82F6),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TOP PREDICTIVE T9 VERTICAL LIST
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (t9Matches.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(t9Matches, key = { if (it.id != 0L) it.id else it.number }) { contact ->
                        val colorIdx = (contact.id.hashCode() and 0x7FFFFFFF) % avatarColors.size
                        val initial = contact.name.trim().firstOrNull()?.uppercaseChar() ?: '?'

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCallClick(contact.number)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!contact.photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(contact.photoUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(avatarColors[colorIdx], CircleShape)
                                            .clip(CircleShape),
                                    ) {
                                        Text(
                                            text = initial.toString(),
                                            style = TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                            ),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${contact.number} • ${contact.phones.firstOrNull()?.label?.ifBlank { "Mobile" } ?: "Mobile"}",
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            color = Color(0xFF8E8E93),
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCallClick(contact.number)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (enteredNumber.isNotEmpty()) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddToContactClick(enteredNumber)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E2024),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Add to contacts",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF38BDF8),
                            ),
                        )
                    }
                }
            }
        }

        // ENTERED NUMBER DISPLAY ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = enteredNumber,
                style = TextStyle(
                    fontSize = if (enteredNumber.length > 13) 26.sp else 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (enteredNumber.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDeletePress()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClearAll()
                            },
                        )
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete digit",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // SAMSUNG ONE UI 3x4 KEYPAD GRID
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            keys.forEach { rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowKeys.forEach { (digit, letters) ->
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF141619),
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        playKeypadTone(digit, keypadToneMode)
                                        onDigitPress(digit)
                                    },
                                    onLongClick = {
                                        if (digit == '0') {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playKeypadTone('+', keypadToneMode)
                                            onDigitPress('+')
                                        } else if (digit == '1') {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onCallClick("1")
                                        }
                                    },
                                ),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = digit.toString(),
                                    style = TextStyle(
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White,
                                    ),
                                )
                                if (letters.isNotEmpty()) {
                                    Text(
                                        text = letters,
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF8E8E93),
                                            letterSpacing = 1.sp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // BOTTOM CENTERED EMERALD CALL POD
        Surface(
            onClick = {
                if (enteredNumber.isNotBlank()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCallClick(enteredNumber)
                }
            },
            shape = CircleShape,
            color = Color(0xFF10B981),
            modifier = Modifier.size(66.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}
