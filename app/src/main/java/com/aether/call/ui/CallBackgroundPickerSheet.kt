package com.aether.call.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiCallBackgroundPickerSheet(
    selectedThemeId: String,
    customBackgroundUri: String?,
    onSelectTheme: (String) -> Unit,
    onSelectCustomPhoto: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            CallBackgroundEngine.persistGalleryUriPermission(context, uri)
            onSelectCustomPhoto(uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2024),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Call screen background",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            // 1. AUTO-EXTRACT FROM CONTACT PHOTO / AVATAR ICON
            val isAutoSelected = customBackgroundUri.isNullOrBlank() &&
                (selectedThemeId == "GRAD_AUTO_AVATAR" || selectedThemeId.contains("Auto", ignoreCase = true))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF282B32))
                    .border(
                        width = if (isAutoSelected) 2.dp else 0.dp,
                        color = if (isAutoSelected) Color(0xFF10B981) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable {
                        onSelectCustomPhoto(null)
                        onSelectTheme("GRAD_AUTO_AVATAR")
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF38BDF8), Color(0xFFA855F7)),
                                ),
                            ),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-extract from Contact Avatar",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Matches dominant colors of contact photo or initial icon",
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp,
                        )
                    }
                    if (isAutoSelected) {
                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF10B981))
                    }
                }
            }

            // 2. CUSTOM PHOTO FROM GALLERY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF282B32))
                    .border(
                        width = if (!customBackgroundUri.isNullOrBlank()) 2.dp else 0.dp,
                        color = if (!customBackgroundUri.isNullOrBlank()) Color(0xFF38BDF8) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                    ) {
                        if (!customBackgroundUri.isNullOrBlank()) {
                            AsyncImage(
                                model = customBackgroundUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!customBackgroundUri.isNullOrBlank()) "Custom Gallery Photo Active" else "Choose Photo from Gallery",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Use any wallpaper or portrait from your phone",
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp,
                        )
                    }
                    if (!customBackgroundUri.isNullOrBlank()) {
                        IconButton(onClick = { onSelectCustomPhoto(null) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove custom photo", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // 3. MULTI-STOP GRADIENT THEMES
            Text(
                text = "Gradient Themes",
                color = Color(0xFF9E9E9E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val gradients = CallBackgroundEngine.gradientPresets.filter { it.category == CallBgCategory.GRADIENT }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gradients.forEach { preset ->
                    val isSelected = customBackgroundUri.isNullOrBlank() &&
                        (selectedThemeId == preset.id || selectedThemeId == preset.title)
                    SwatchPreviewCard(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = {
                            onSelectCustomPhoto(null)
                            onSelectTheme(preset.id)
                        },
                    )
                }
            }

            // 4. SOLID COLOR THEMES
            Text(
                text = "Solid Colors",
                color = Color(0xFF9E9E9E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val solids = CallBackgroundEngine.solidPresets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                solids.forEach { preset ->
                    val isSelected = customBackgroundUri.isNullOrBlank() &&
                        (selectedThemeId == preset.id || selectedThemeId == preset.title)
                    SwatchPreviewCard(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = {
                            onSelectCustomPhoto(null)
                            onSelectTheme(preset.id)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SwatchPreviewCard(
    preset: CallBackgroundPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 76.dp, height = 104.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(preset.colors))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF3A3D45),
                    shape = RoundedCornerShape(18.dp),
                ),
        ) {
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981),
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = preset.title,
            color = if (isSelected) Color(0xFF10B981) else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}