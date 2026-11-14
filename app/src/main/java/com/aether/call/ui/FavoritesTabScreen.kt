package com.aether.call.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aether.call.data.ContactItem
import java.util.Locale

@Composable
fun FavoritesTabScreen(
    favoriteContacts: List<ContactItem>,
    onContactTap: (ContactItem) -> Unit,
    onContactCall: (String) -> Unit,
    onAddFavoriteClick: () -> Unit,
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val pastelColors = listOf(
        Color(0xFFEC4899),
        Color(0xFFF97316),
        Color(0xFF06B6D4),
        Color(0xFF14B8A6),
        Color(0xFFA855F7),
        Color(0xFF3B82F6),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
    ) {
        if (favoriteContacts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0x22FBBF24), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(40.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "No favorite contacts yet",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Star contacts to add them to your speed dial list.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                    ),
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAddFavoriteClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("Add Favorites", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "★ Speed Dial Favorites",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93),
                        ),
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }

                items(favoriteContacts, key = { if (it.id != 0L) it.id else it.number }) { contact ->
                    val colorIdx = (contact.id.hashCode() and 0x7FFFFFFF) % pastelColors.size
                    val avatarColor = pastelColors[colorIdx]
                    val initial = contact.name.trimStart().take(1).uppercase(Locale.getDefault())

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onContactTap(contact)
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(avatarColor, CircleShape)
                                        .clip(CircleShape),
                                ) {
                                    if (!contact.photoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(contact.photoUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = contact.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Text(
                                            text = initial.ifEmpty { "?" },
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                            ),
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                        ),
                                    )
                                    Text(
                                        text = contact.number,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF8E8E93),
                                            fontSize = 13.sp,
                                        ),
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { onMessage(contact.number) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Message,
                                        contentDescription = "Message",
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(onClick = { onContactCall(contact.number) }) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
