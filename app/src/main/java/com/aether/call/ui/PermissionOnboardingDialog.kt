package com.aether.call.ui

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

object DialerPermissionHelper {

    val REQUIRED_RUNTIME_PERMISSIONS: Array<String>
        get() = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            tm?.defaultDialerPackage == context.packageName
        }
    }

    fun hasAllRuntimePermissions(context: Context): Boolean {
        return REQUIRED_RUNTIME_PERMISSIONS.all { perm ->
            context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.canUseFullScreenIntent() != false
        } else true
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openAppInfoForRestrictedSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@Composable
fun PermissionSetupBannerOrSheet() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isDefault by remember { mutableStateOf(DialerPermissionHelper.isDefaultDialer(context)) }
    var hasRuntimePerms by remember { mutableStateOf(DialerPermissionHelper.hasAllRuntimePermissions(context)) }
    var hasFullScreenIntent by remember { mutableStateOf(DialerPermissionHelper.canUseFullScreenIntent(context)) }
    var hasOverlay by remember { mutableStateOf(DialerPermissionHelper.canDrawOverlays(context)) }

    val refreshStatus = {
        isDefault = DialerPermissionHelper.isDefaultDialer(context)
        hasRuntimePerms = DialerPermissionHelper.hasAllRuntimePermissions(context)
        hasFullScreenIntent = DialerPermissionHelper.canUseFullScreenIntent(context)
        hasOverlay = DialerPermissionHelper.canDrawOverlays(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshStatus() }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    if (isDefault && hasRuntimePerms && hasFullScreenIntent && hasOverlay) {
        return
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E2024),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Complete Setup for Full Call Control",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Because Aether Call was installed via APK, enable Restricted Settings in App Info first if Android blocks permissions.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Step 1: Unlock Restricted Settings (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!hasRuntimePerms || !isDefault)) {
                PermissionActionRow(
                    icon = Icons.Default.LockOpen,
                    title = "1. Allow Restricted Settings",
                    subtitle = "Tap ⋮ (top-right) in App Info → 'Allow restricted settings'",
                    isGranted = false,
                    onClick = { DialerPermissionHelper.openAppInfoForRestrictedSettings(context) }
                )
            }

            // Step 2: Set as Default Phone App
            PermissionActionRow(
                icon = Icons.AutoMirrored.Filled.PhoneCallback,
                title = "Set Aether Call as Default Phone App",
                subtitle = if (isDefault) "Active default dialer" else "Required to block native dialer & handle calls",
                isGranted = isDefault,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rm = context.getSystemService(RoleManager::class.java)
                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                        }
                        roleLauncher.launch(intent)
                    }
                }
            )

            // Step 3: Runtime Permissions (Contacts, Call Log, Audio, Camera, SMS)
            PermissionActionRow(
                icon = Icons.Default.Security,
                title = "Grant Call, Contact & Recording Permissions",
                subtitle = if (hasRuntimePerms) "All permissions granted" else "Tap to allow Contacts, Call Logs, Mic & Camera",
                isGranted = hasRuntimePerms,
                onClick = {
                    runtimePermLauncher.launch(DialerPermissionHelper.REQUIRED_RUNTIME_PERMISSIONS)
                }
            )

            // Step 4: Full-Screen Incoming Call Intent (Android 14+)
            if (!hasFullScreenIntent) {
                PermissionActionRow(
                    icon = Icons.Default.Security,
                    title = "Allow Full-Screen Call Alerts",
                    subtitle = "Prevents native OEM dialer from taking over lock screen",
                    isGranted = false,
                    onClick = { DialerPermissionHelper.openFullScreenIntentSettings(context) }
                )
            }

            // Step 5: Draw Over Other Apps
            if (!hasOverlay) {
                PermissionActionRow(
                    icon = Icons.Default.Security,
                    title = "Allow Appear on Top (Overlay)",
                    subtitle = "Allows instant call screen launch from background",
                    isGranted = false,
                    onClick = { DialerPermissionHelper.openOverlayPermissionSettings(context) }
                )
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isGranted) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF282C34))
            .clickable(enabled = !isGranted) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF10B981) else Color(0xFF38BDF8),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        if (!isGranted) {
            Text(
                text = "Enable",
                color = Color(0xFF38BDF8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
