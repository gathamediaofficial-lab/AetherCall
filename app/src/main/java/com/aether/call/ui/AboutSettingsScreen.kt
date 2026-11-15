package com.aether.call.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.call.util.GitHubUpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    appVersionName: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolvedVersion = remember(context, appVersionName) {
        appVersionName?.takeIf { it.isNotBlank() } ?: try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResultDialog by remember { mutableStateOf<GitHubUpdateChecker.UpdateInfo?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }

    // Full-screen Dialog ensures independent window bounds (Zero infinite height crashes even if placed inside LazyColumn)
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = onBack)

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
                        text = "About Aether Call",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            },
            modifier = modifier.fillMaxSize(),
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // App Identity Header Card
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF1E2024),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Aether Call",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Production-Grade Secure Android Dialer",
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2A2D35),
                        ) {
                            Text(
                                text = "Version $resolvedVersion (Stable)",
                                color = Color(0xFF6EE7B7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // What the App Does & Architecture
                Column {
                    Text(
                        text = "App Overview & Capabilities",
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "What is Aether Call?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "Aether Call is a high-performance, aesthetically premium native Android dialer application built with Kotlin and Jetpack Compose. It integrates directly with Android's official InCallService and CallScreeningService roles to deliver flawless VoLTE voice calls, Carrier ViLTE HD video streams, secure local call recording into MediaStore (Music/Aether Call Recordings), and multi-account contact sync.",
                                fontSize = 14.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }

                // Privacy, Safety & Data Integrity
                Column {
                    Text(
                        text = "Privacy, Security & Data Integrity",
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
                            AboutInfoRow(
                                icon = Icons.Default.Lock,
                                iconTint = Color(0xFF38BDF8),
                                title = "Zero Data Collection",
                                subtitle = "Developer does not collect, track, or upload any personal data, call logs, or contacts.",
                            )
                            HorizontalDivider(
                                color = Color(0xFF2E3138),
                                thickness = 0.6.dp,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            AboutInfoRow(
                                icon = Icons.Default.Security,
                                iconTint = Color(0xFF10B981),
                                title = "Local-First Architecture",
                                subtitle = "All contacts, settings, and call recordings remain 100% private inside your local device storage.",
                            )
                            HorizontalDivider(
                                color = Color(0xFF2E3138),
                                thickness = 0.6.dp,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            AboutInfoRow(
                                icon = Icons.Default.Info,
                                iconTint = Color(0xFFFBBF24),
                                title = "Telecom Sandbox Integrity",
                                subtitle = "Operates strictly within official Android OS security boundaries with encrypted SIM authentication.",
                            )
                        }
                    }
                }

                // GitHub Auto Update Checker & Source Code
                Column {
                    Text(
                        text = "Updates & Open Source",
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
                            // Check for updates row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isCheckingUpdate) {
                                            isCheckingUpdate = true
                                            coroutineScope.launch {
                                                val result = GitHubUpdateChecker.checkForUpdates(context, resolvedVersion)
                                                isCheckingUpdate = false
                                                if (result != null) {
                                                    updateResultDialog = result
                                                    showUpdateSheet = true
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Unable to reach GitHub releases or already on latest version.",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isCheckingUpdate) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color(0xFF8B5CF6),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Check for updates",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isCheckingUpdate) "Checking GitHub repository..." else "Check if a new version is available",
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

                            // GitHub Repository Link Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        GitHubUpdateChecker.openWebPage(
                                            context,
                                            "https://github.com/gathamediaofficial-lab/AetherCall",
                                        )
                                    }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF334155).copy(alpha = 0.5f),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "View source code on GitHub",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "github.com/gathamediaofficial-lab/AetherCall",
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

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Update Available / Status Bottom Sheet
        if (showUpdateSheet && updateResultDialog != null) {
            val info = updateResultDialog!!
            ModalBottomSheet(
                onDismissRequest = { showUpdateSheet = false },
                containerColor = Color(0xFF1E2024),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (info.hasUpdate) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF3B82F6).copy(alpha = 0.2f),
                        modifier = Modifier.size(60.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (info.hasUpdate) Icons.Default.Refresh else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (info.hasUpdate) Color(0xFF10B981) else Color(0xFF3B82F6),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (info.hasUpdate) "New Version Available!" else "You're Up to Date!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Latest Release: v${info.latestVersion}",
                        fontSize = 14.sp,
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF141619),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Release Notes:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = info.releaseNotes.ifBlank { "Performance improvements and stability fixes." },
                                fontSize = 13.sp,
                                color = Color.White,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (info.hasUpdate) {
                        Surface(
                            onClick = {
                                showUpdateSheet = false
                                GitHubUpdateChecker.openWebPage(context, info.downloadUrl)
                            },
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Download Update from GitHub",
                                    color = Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = { showUpdateSheet = false },
                            shape = CircleShape,
                            color = Color(0xFF2A2D35),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Close",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.2f),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                lineHeight = 16.sp,
            )
        }
    }
}