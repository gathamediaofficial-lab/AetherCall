package com.aether.call

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.aether.call.ui.DefaultDialerPromptScreen
import com.aether.call.ui.DialerMainScreen
import com.aether.call.ui.DialerViewModel

/**
 * Main Activity for Aether Call hosting the Samsung One UI Jetpack Compose interface
 * with unified runtime & special-access permission request handling.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        // Return from overlay permission settings
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        viewModel.checkRoleStatus(this)
        checkSpecialPermissions()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        viewModel.loadContacts(this)
        viewModel.loadRecentCalls(this)
        checkSpecialPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )

        ensureDefaultDialerAndFullScreenPermission(this)
        viewModel.checkRoleStatus(this)
        requestAppPermissions()

        setContent {
            val isDefault by viewModel.isDefaultDialer.collectAsState()

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF000000),
            ) {
                if (!isDefault) {
                    DefaultDialerPromptScreen(
                        onRequestDefaultDialer = { launchSetDefaultDialer() },
                    )
                } else {
                    DialerMainScreen(
                        viewModel = viewModel,
                        onRequestDefaultDialer = { launchSetDefaultDialer() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkRoleStatus(this)
        if (hasRequiredPermissions()) {
            viewModel.loadContacts(this)
            viewModel.loadRecentCalls(this)
        }
    }

    fun launchSetDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                roleLauncher.launch(intent)
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            roleLauncher.launch(intent)
        }
    }

    private fun checkSpecialPermissions() {
        // 1. Default Dialer Role Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                roleLauncher.launch(intent)
                return
            }
        }

        // 2. Display Over Other Apps (Overlay) Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
                overlayLauncher.launch(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildVersionAwarePermissionsList(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        // Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        // Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        // Android 14+ (API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }

        // Android 12L and below (API <= 32)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            @Suppress("DEPRECATION")
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permissions
    }

    private fun hasRequiredPermissions(): Boolean {
        val required = buildVersionAwarePermissionsList()
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAppPermissions() {
        val allPermissions = buildVersionAwarePermissionsList()
        val ungranted = allPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted)
        } else {
            viewModel.loadContacts(this)
            viewModel.loadRecentCalls(this)
            checkSpecialPermissions()
        }
    }
}

fun ensureDefaultDialerAndFullScreenPermission(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, 1001)
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val nm = activity.getSystemService(NotificationManager::class.java)
        if (nm != null && !nm.canUseFullScreenIntent()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(intent)
        }
    }
}
