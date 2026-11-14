package com.aether.call.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.call.data.IncomingCallLayoutStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(
    currentThemeId: String,
    currentCustomUri: String?,
    currentIncomingLayoutId: String = IncomingCallLayoutStyle.SLIDE_TO_ANSWER.id,
    onUpdateTheme: (String) -> Unit,
    onUpdateCustomUri: (String?) -> Unit,
    onUpdateIncomingLayout: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBgPickerSheet by remember { mutableStateOf(false) }
    var showIncomingLayoutSheet by remember { mutableStateOf(false) }

    val selectedLayoutStyle = remember(currentIncomingLayoutId) {
        IncomingCallLayoutStyle.fromId(currentIncomingLayoutId)
    }

    Scaffold(
        containerColor = Color(0xFF000000),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Call Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Text(
                    text = "Call Appearance & Personalization",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8E8E93),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E2024),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Row 1: Call Screen Background
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBgPickerSheet = true }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Call screen background",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        !currentCustomUri.isNullOrBlank() -> "Custom Gallery Photo"
                                        currentThemeId.contains("Auto", ignoreCase = true) -> "Auto-extract from Contact Avatar"
                                        else -> CallBackgroundEngine.allPresets.find { it.id == currentThemeId }?.title ?: currentThemeId
                                    },
                                    fontSize = 13.sp,
                                    color = Color(0xFF9E9E9E),
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                            )
                        }

                        HorizontalDivider(
                            color = Color(0xFF2E3138),
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )

                        // Row 2: Incoming Call Layout Style (Slide vs Dual Buttons)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showIncomingLayoutSheet = true }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallReceived,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Incoming call style",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = selectedLayoutStyle.title,
                                    fontSize = 13.sp,
                                    color = Color(0xFF9E9E9E),
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBgPickerSheet) {
        OneUiCallBackgroundPickerSheet(
            selectedThemeId = currentThemeId,
            customBackgroundUri = currentCustomUri,
            onSelectTheme = { newTheme -> onUpdateTheme(newTheme) },
            onSelectCustomPhoto = { newUri -> onUpdateCustomUri(newUri) },
            onDismiss = { showBgPickerSheet = false },
        )
    }

    if (showIncomingLayoutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showIncomingLayoutSheet = false },
            containerColor = Color(0xFF1E2024),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Incoming call style",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                IncomingCallLayoutStyle.entries.forEach { styleOption ->
                    val isSelected = styleOption == selectedLayoutStyle
                    Surface(
                        onClick = {
                            onUpdateIncomingLayout(styleOption.id)
                            showIncomingLayoutSheet = false
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF282B32),
                        border = if (isSelected) BorderStroke(2.dp, Color(0xFF10B981)) else null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = styleOption.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = styleOption.subtitle,
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 12.sp,
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF10B981),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}