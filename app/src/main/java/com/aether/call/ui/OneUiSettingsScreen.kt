package com.aether.call.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.aether.call.data.IncomingCallLayoutStyle
import com.aether.call.util.GitHubUpdateChecker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.call.data.UserAppSettings

enum class SettingsSubPage {
    BLOCK_NUMBERS,
    RECORD_CALLS,
    CALL_BACKGROUND,
    CALLER_INFO,
    ALERTS_RINGTONE,
    ANSWERING_ENDING,
    CALL_DISPLAY,
    SUPPLEMENTARY_SERVICES,
    OTHER_SETTINGS,
    ABOUT,
}

private fun safeLaunchIntent(context: Context, intent: Intent) {
    try {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Not supported on this carrier/device", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Not supported on this carrier/device", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Authentic Samsung One UI Call Settings Screen with Sub-Pages and DataStore persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSettingsScreen(
    viewModel: DialerViewModel,
    onNavigateBack: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onOpenBlockNumbers: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val userSettings by viewModel.userSettings.collectAsState()

    var activeSubPage by rememberSaveable { mutableStateOf<SettingsSubPage?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSimModeDialog by remember { mutableStateOf(false) }
    var showIncomingLayoutSheet by remember { mutableStateOf(false) }
    var updateInfoState by remember { mutableStateOf<GitHubUpdateChecker.UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = activeSubPage != null) {
        activeSubPage = null
    }

    if (activeSubPage == SettingsSubPage.ABOUT) {
        val dynamicVersion = remember(context) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.1"
            } catch (_: Exception) {
                "1.1.1"
            }
        }
        AboutSettingsScreen(
            appVersionName = dynamicVersion,
            onBack = { activeSubPage = null },
        )
    } else if (activeSubPage != null) {
        SubPageContainer(
            subPage = activeSubPage!!,
            userSettings = userSettings,
            viewModel = viewModel,
            onBack = { activeSubPage = null },
            onOpenRecordings = onOpenRecordings,
        )
    } else {
        Scaffold(
            containerColor = Color(0xFF000000),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Text(
                        text = "Call settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // CARD 1
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            SettingsTile(
                                title = "Block numbers",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenBlockNumbers()
                                    activeSubPage = SettingsSubPage.BLOCK_NUMBERS
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsSwitchTile(
                                title = "Caller ID and spam protection",
                                checked = userSettings.spamShieldEnabled,
                                onCheckedChange = {
                                    viewModel.updateUserSettings(context) { s -> s.copy(spamShieldEnabled = it) }
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Record calls",
                                subtitle = if (userSettings.autoRecordCalls) "Auto recording on" else "Auto recording off",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.RECORD_CALLS
                                },
                            )
                        }
                    }
                }

                // CARD 2
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            SettingsTile(
                                title = "Call background",
                                subtitle = userSettings.callBackgroundTheme,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.CALL_BACKGROUND
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Incoming call style",
                                subtitle = IncomingCallLayoutStyle.fromId(userSettings.incomingCallLayoutStyle).title,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showIncomingLayoutSheet = true
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Caller information",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.CALLER_INFO
                                },
                            )
                        }
                    }
                }

                // CARD 3
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            SettingsTile(
                                title = "Call alerts and ringtone",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.ALERTS_RINGTONE
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Answering and ending calls",
                                subtitle = "Flip to silence ${if (userSettings.flipToSilence) "enabled" else "disabled"}",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.ANSWERING_ENDING
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Quick decline messages",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenQuickReplies()
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Call display while using apps",
                                subtitle = userSettings.callDisplayMode,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.CALL_DISPLAY
                                },
                            )
                        }
                    }
                }

                // CARD 4
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            SettingsTile(
                                title = "SIM card manager / Default calling SIM",
                                subtitle = userSettings.defaultSimMode,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSimModeDialog = true
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Wi-Fi Calling",
                                subtitle = "Wi-Fi preferred",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    safeLaunchIntent(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Voicemail",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    safeLaunchIntent(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Supplementary services",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.SUPPLEMENTARY_SERVICES
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "Other call settings",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.OTHER_SETTINGS
                                },
                            )
                        }
                    }
                }

                // CARD 5
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            SettingsTile(
                                title = "Permissions",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    safeLaunchIntent(
                                        context,
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        ),
                                    )
                                },
                            )

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                            SettingsTile(
                                title = "About Aether Call",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeSubPage = SettingsSubPage.ABOUT
                                },
                            )
                        }
                    }
                }
            }
        }

        // SIM Mode Dialog
        if (showSimModeDialog) {
            val simModes = listOf("Ask always", "SIM 1", "SIM 2")
            AlertDialog(
                onDismissRequest = { showSimModeDialog = false },
                containerColor = Color(0xFF1E2024),
                title = @Composable {
                    Text("Default calling SIM", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = @Composable {
                    Column {
                        simModes.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val slotIndex = when (mode) {
                                            "SIM 1" -> 0
                                            "SIM 2" -> 1
                                            else -> -1
                                        }
                                        viewModel.updateUserSettings(context) { s ->
                                            s.copy(defaultSimMode = mode, defaultSimSlot = slotIndex)
                                        }
                                        showSimModeDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = userSettings.defaultSimMode == mode,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val slotIndex = when (mode) {
                                            "SIM 1" -> 0
                                            "SIM 2" -> 1
                                            else -> -1
                                        }
                                        viewModel.updateUserSettings(context) { s ->
                                            s.copy(defaultSimMode = mode, defaultSimSlot = slotIndex)
                                        }
                                        showSimModeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981)),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(mode, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSimModeDialog = false }) {
                        Text("Cancel", color = Color(0xFF8E8E93))
                    }
                },
            )
        }

        // About & Update Check Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAboutDialog = false
                    updateInfoState = null
                },
                containerColor = Color(0xFF1E2024),
                title = @Composable {
                    Text(
                        text = "Aether Call • One UI Edition",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = @Composable {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Version 1.0.0",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Pure Samsung One UI System Phone Dialer with DataStore Settings & Material 3 Compose.",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                        )

                        if (updateInfoState != null) {
                            HorizontalDivider(color = Color(0xFF2E3138), modifier = Modifier.padding(vertical = 4.dp))
                            val update = updateInfoState!!
                            if (update.hasUpdate) {
                                Text(
                                    text = "🚀 New Version Available: v${update.latestVersion}",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = update.releaseNotes,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                )
                            } else {
                                Text(
                                    text = " You are on the latest version (v1.0.0)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (updateInfoState?.hasUpdate == true) {
                                    GitHubUpdateChecker.openReleasePage(context, updateInfoState!!.downloadUrl)
                                } else {
                                    isCheckingUpdate = true
                                    coroutineScope.launch {
                                        updateInfoState = GitHubUpdateChecker.checkForUpdates(context, "1.0.0")
                                        isCheckingUpdate = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                text = when {
                                    isCheckingUpdate -> "Checking..."
                                    updateInfoState?.hasUpdate == true -> "Download v${updateInfoState?.latestVersion}"
                                    else -> "Check for Updates"
                                },
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Button(
                            onClick = {
                                showAboutDialog = false
                                updateInfoState = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        ) {
                            Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
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

                    val selectedStyle = IncomingCallLayoutStyle.fromId(userSettings.incomingCallLayoutStyle)

                    IncomingCallLayoutStyle.entries.forEach { styleOption ->
                        val isSelected = styleOption == selectedStyle
                        Surface(
                            onClick = {
                                viewModel.updateUserSettings(context) { s -> s.copy(incomingCallLayoutStyle = styleOption.id) }
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
}

@Composable
private fun SubPageContainer(
    subPage: SettingsSubPage,
    userSettings: UserAppSettings,
    viewModel: DialerViewModel,
    onBack: () -> Unit,
    onOpenRecordings: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            viewModel.updateUserSettings(context) { s ->
                s.copy(callBackgroundTheme = "Custom Photo", customCallBackgroundUri = uri.toString())
            }
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val intent = result.data
            @Suppress("DEPRECATION")
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri != null) {
                try {
                    RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, pickedUri)
                    val ringtoneTitle = RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context) ?: "Selected Ringtone"
                    Toast.makeText(context, "Ringtone updated: $ringtoneTitle", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Ringtone updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val title = when (subPage) {
        SettingsSubPage.BLOCK_NUMBERS -> "Block numbers"
        SettingsSubPage.RECORD_CALLS -> "Record calls"
        SettingsSubPage.CALL_BACKGROUND -> "Call background"
        SettingsSubPage.CALLER_INFO -> "Caller information"
        SettingsSubPage.ALERTS_RINGTONE -> "Call alerts and ringtone"
        SettingsSubPage.ANSWERING_ENDING -> "Answering and ending calls"
        SettingsSubPage.CALL_DISPLAY -> "Call display while using apps"
        SettingsSubPage.SUPPLEMENTARY_SERVICES -> "Supplementary services"
        SettingsSubPage.OTHER_SETTINGS -> "Other call settings"
        else -> ""
    }

    Scaffold(
        containerColor = Color(0xFF000000),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (subPage) {
                SettingsSubPage.BLOCK_NUMBERS -> {
                    item {
                        var numberToBlock by remember { mutableStateOf("") }

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SettingsSwitchTile(
                                    title = "Block calls from unknown numbers",
                                    checked = userSettings.blockUnknownNumbers,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(blockUnknownNumbers = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                                Text(
                                    text = "Add phone number",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF8E8E93),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedTextField(
                                        value = numberToBlock,
                                        onValueChange = { numberToBlock = it },
                                        placeholder = { Text("Enter number to block...", color = Color(0xFF8E8E93)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF141619),
                                            unfocusedContainerColor = Color(0xFF141619),
                                            focusedBorderColor = Color(0xFF10B981),
                                            unfocusedBorderColor = Color(0xFF2E3138),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )

                                    IconButton(
                                        onClick = {
                                            if (numberToBlock.isNotBlank()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.addBlockedNumber(context, numberToBlock.trim())
                                                numberToBlock = ""
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Blocked numbers list (${userSettings.blockedNumbers.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF8E8E93),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )

                                if (userSettings.blockedNumbers.isEmpty()) {
                                    Text(
                                        text = "No blocked numbers",
                                        fontSize = 14.sp,
                                        color = Color(0xFF6B7280),
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                } else {
                                    userSettings.blockedNumbers.forEach { num ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(text = num, color = Color.White, fontSize = 16.sp)

                                            IconButton(onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.removeBlockedNumber(context, num)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.RemoveCircle,
                                                    contentDescription = "Remove",
                                                    tint = Color(0xFFEF4444),
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSubPage.RECORD_CALLS -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                SettingsSwitchTile(
                                    title = "Show notification after recording",
                                    checked = userSettings.showRecordingNotification,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(showRecordingNotification = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Auto record calls",
                                    checked = userSettings.autoRecordCalls,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(autoRecordCalls = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsTile(
                                    title = "Recorded calls",
                                    subtitle = "View and play call recording files",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenRecordings()
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSubPage.CALL_BACKGROUND -> {
                    item {
                        OneUiCallBackgroundPickerSheet(
                            selectedThemeId = userSettings.callBackgroundTheme,
                            customBackgroundUri = userSettings.customCallBackgroundUri,
                            onSelectTheme = { newTheme ->
                                viewModel.updateUserSettings(context) { s -> s.copy(callBackgroundTheme = newTheme) }
                            },
                            onSelectCustomPhoto = { newUri ->
                                viewModel.updateUserSettings(context) { s ->
                                    s.copy(
                                        customCallBackgroundUri = newUri,
                                        callBackgroundTheme = if (!newUri.isNullOrBlank()) "CUSTOM_PHOTO" else s.callBackgroundTheme,
                                    )
                                }
                            },
                            onDismiss = { onBack() },
                        )
                    }
                }

                SettingsSubPage.CALLER_INFO -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                SettingsSwitchTile(
                                    title = "Show recent call history in call",
                                    subtitle = "Displays caller's last calls during active ringing",
                                    checked = userSettings.showCallerHistoryInCall,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(showCallerHistoryInCall = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Show carrier & SIM badge",
                                    checked = userSettings.showCarrierLocationTag,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(showCarrierLocationTag = it) }
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSubPage.ALERTS_RINGTONE -> {
                    item {
                        val keypadModes = listOf("Default", "Retro", "Off")

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                SettingsSwitchTile(
                                    title = "Vibrate when answered",
                                    checked = userSettings.vibrateOnAnswer,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(vibrateOnAnswer = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Vibrate when call ends",
                                    checked = userSettings.vibrateOnEnd,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(vibrateOnEnd = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Play tone when call connects",
                                    checked = userSettings.playConnectTone,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(playConnectTone = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Play tone when call ends",
                                    checked = userSettings.playEndTone,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(playEndTone = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Keypad touch vibration",
                                    checked = userSettings.hapticsEnabled,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(hapticsEnabled = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Keypad tones", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    keypadModes.forEach { mode ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.updateUserSettings(context) { s -> s.copy(keypadToneMode = mode) }
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = userSettings.keypadToneMode == mode,
                                                onClick = {
                                                    viewModel.updateUserSettings(context) { s -> s.copy(keypadToneMode = mode) }
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981)),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(mode, color = Color.White, fontSize = 15.sp)
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsTile(
                                    title = "Ringtone",
                                    subtitle = "System Ringtone",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
                                            )
                                        }
                                        try {
                                            ringtoneLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Not supported on this carrier/device", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSubPage.ANSWERING_ENDING -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                SettingsSwitchTile(
                                    title = "Read caller names aloud",
                                    subtitle = "Uses TextToSpeech on incoming call",
                                    checked = userSettings.readCallerNameAloud,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(readCallerNameAloud = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Answer automatically",
                                    subtitle = "Answers incoming calls automatically after a delay",
                                    checked = userSettings.autoAnswerEnabled,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(autoAnswerEnabled = it) }
                                    },
                                )

                                if (userSettings.autoAnswerEnabled) {
                                    val delays = listOf(2, 5, 10)
                                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                        delays.forEach { delaySecs ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.updateUserSettings(context) { s -> s.copy(autoAnswerDelaySeconds = delaySecs) }
                                                    }
                                                    .padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = userSettings.autoAnswerDelaySeconds == delaySecs,
                                                    onClick = {
                                                        viewModel.updateUserSettings(context) { s -> s.copy(autoAnswerDelaySeconds = delaySecs) }
                                                    },
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981)),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("After $delaySecs seconds", color = Color.White, fontSize = 15.sp)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Press Volume up to answer",
                                    checked = userSettings.volumeUpToAnswer,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(volumeUpToAnswer = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Press Side key to end calls",
                                    checked = userSettings.powerKeyEndsCall,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(powerKeyEndsCall = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Flip to silence",
                                    checked = userSettings.flipToSilence,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(flipToSilence = it) }
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSubPage.CALL_DISPLAY -> {
                    item {
                        val displayModes = listOf("Full screen", "Small pop-up", "Mini pop-up")

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Call display mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )

                                displayModes.forEach { mode ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.updateUserSettings(context) { s -> s.copy(callDisplayMode = mode) }
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = userSettings.callDisplayMode == mode,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.updateUserSettings(context) { s -> s.copy(callDisplayMode = mode) }
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981)),
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = mode,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                                SettingsSwitchTile(
                                    title = "Keep calls in pop-up",
                                    subtitle = "Keep pop-up window after call is answered",
                                    checked = userSettings.keepCallsInPopup,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(keepCallsInPopup = it) }
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSubPage.SUPPLEMENTARY_SERVICES -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                SettingsTile(
                                    title = "Call forwarding",
                                    subtitle = "Always forward, when busy, unanswered, unreachable",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        safeLaunchIntent(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsTile(
                                    title = "Call waiting & Caller ID",
                                    subtitle = "Manage call waiting alerts & outbound ID display",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        safeLaunchIntent(context, Intent("android.settings.NETWORK_OPERATOR_SETTINGS"))
                                    },
                                )
                            }
                        }
                    }
                }

                SettingsSubPage.OTHER_SETTINGS -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                SettingsSwitchTile(
                                    title = "Hide contacts without numbers",
                                    checked = userSettings.hideContactsWithoutNumbers,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(hideContactsWithoutNumbers = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Auto-open to Keypad on launch",
                                    checked = userSettings.openToKeypad,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(openToKeypad = it) }
                                    },
                                )

                                HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)

                                SettingsSwitchTile(
                                    title = "Assisted dialing (+91 India)",
                                    checked = userSettings.assistedDialingIndia,
                                    onCheckedChange = {
                                        viewModel.updateUserSettings(context) { s -> s.copy(assistedDialingIndia = it) }
                                    },
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun SettingsTile(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White,
                ),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF10B981),
                        fontSize = 13.sp,
                    ),
                )
            }
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchTile(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White,
                ),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF10B981),
                        fontSize = 13.sp,
                    ),
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF10B981),
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF2E3138),
            ),
        )
    }
}
