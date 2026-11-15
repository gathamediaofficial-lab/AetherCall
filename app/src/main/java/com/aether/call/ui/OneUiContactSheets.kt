package com.aether.call.ui

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aether.call.data.CallLogItem
import com.aether.call.data.ContactItem
import com.aether.call.data.getContactInitial
import com.aether.call.data.ContactsRepository
import com.aether.call.data.LabeledValue
import com.aether.call.util.PhoneNormalizer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun generateQrCodeBitmap(content: String, sizePx: Int = 512): ImageBitmap {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

fun buildVCardString(contact: ContactItem): String {
    val sb = StringBuilder()
    sb.appendLine("BEGIN:VCARD")
    sb.appendLine("VERSION:3.0")
    val fn = contact.displayName.ifBlank { contact.primaryNumber }
    sb.appendLine("N:${contact.lastName};${contact.firstName};${contact.middleName};${contact.prefix};${contact.suffix}")
    sb.appendLine("FN:$fn")
    contact.phones.forEach { p ->
        val lbl = if (p.label.isNotBlank()) p.label else "CELL"
        sb.appendLine("TEL;TYPE=$lbl:${p.value}")
    }
    contact.emails.forEach { e ->
        val lbl = if (e.label.isNotBlank()) e.label else "INTERNET"
        sb.appendLine("EMAIL;TYPE=$lbl:${e.value}")
    }
    contact.addresses.forEach { a ->
        val lbl = if (a.label.isNotBlank()) a.label else "HOME"
        sb.appendLine("ADR;TYPE=$lbl:;;${a.value};;;;")
    }
    contact.events.forEach { ev ->
        sb.appendLine("BDAY:${ev.value}")
    }
    sb.appendLine("END:VCARD")
    return sb.toString()
}

private fun safeLaunchIntent(context: Context, intent: Intent) {
    try {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No compatible app found", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}

/**
 * Launches the user's Default Calendar App pre-populated with a recurring annual Birthday / Event reminder.
 */
private fun syncBirthdayToCalendarWithReminder(
    context: Context,
    contactName: String,
    eventLabel: String,
    dateString: String,
) {
    try {
        val cal = Calendar.getInstance()
        val parts = dateString.split("-", "/")
        if (parts.size >= 3) {
            val year = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
            val month = (parts[1].toIntOrNull() ?: 1) - 1
            val day = parts[2].toIntOrNull() ?: 1
            val currentYear = cal.get(Calendar.YEAR)
            cal.set(currentYear, month.coerceIn(0, 11), day.coerceIn(1, 31), 9, 0, 0)
            if (year > 1900 && cal.timeInMillis < System.currentTimeMillis()) {
                cal.add(Calendar.YEAR, 1)
            }
        }

        val startMillis = cal.timeInMillis
        val endMillis = startMillis + 60 * 60 * 1000L

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "$contactName's ${eventLabel.ifBlank { "Birthday" }}")
            putExtra(CalendarContract.Events.DESCRIPTION, "Reminder synced from Aether Call for $contactName ($dateString)")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            putExtra(CalendarContract.Events.HAS_ALARM, 1)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Calendar app not available", Toast.LENGTH_SHORT).show()
    }
}

private fun launchEmailAppChooser(context: Context, emailAddress: String) {
    if (emailAddress.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(emailAddress.trim())}")
        }
        val chooser = Intent.createChooser(intent, "Send email using...").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "No email app installed", Toast.LENGTH_SHORT).show()
    }
}

private fun launchMapsNavigation(context: Context, address: String) {
    if (address.isBlank()) return
    try {
        val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(address.trim())}")
        val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "No maps app installed", Toast.LENGTH_SHORT).show()
    }
}

private fun openSystemEmergencySosSettings(context: Context) {
    val actions = listOf(
        "com.samsung.android.MTP.action.EMERGENCY_SOS",
        "android.settings.EMERGENCY_INFO_SETTINGS",
        "android.settings.SAFETY_EMERGENCY_SETTINGS",
        android.provider.Settings.ACTION_SETTINGS,
    )
    for (act in actions) {
        try {
            val intent = Intent(act).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
}

/**
 * Authentic Samsung One UI Saved Contact Card Screen with:
 * - Instant Star/Favourite button in Top Bar & Bottom Bar
 * - Primary/Default Phone Number switcher (when multiple numbers exist)
 * - Multiple Emails (with Open-With Email App chooser)
 * - Multiple Addresses (synced with Default Maps navigation)
 * - Birthdays & Important Dates (with 1-tap Sync to Calendar & Reminder)
 * - Emergency Contact (ICE / SOS) toggle & system SOS sync
 * - Multi-location Storage dropdown & cloner
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiContactCardScreen(
    contact: ContactItem,
    recentLogsForContact: List<CallLogItem> = emptyList(),
    allCallLogs: List<CallLogItem> = emptyList(),
    isMyProfile: Boolean = false,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onVideoCallClick: ((String) -> Unit)? = null,
    onToggleStar: (ContactItem) -> Unit,
    onEditClick: (ContactItem) -> Unit,
    onShareClick: (ContactItem) -> Unit = {},
    onDeleteClick: (ContactItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var pendingVideoNumber by remember { mutableStateOf<String?>(null) }

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callGranted = permissions[android.Manifest.permission.CALL_PHONE] == true
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
        val targetNum = pendingVideoNumber
        pendingVideoNumber = null
        if (callGranted && cameraGranted && !targetNum.isNullOrBlank()) {
            com.aether.call.data.CallLogRepository.placeCarrierVideoCall(context, targetNum)
        } else if (!targetNum.isNullOrBlank()) {
            Toast.makeText(context, "Camera and Phone permissions are required for Video Calls", Toast.LENGTH_SHORT).show()
        }
    }

    val triggerVideoCall: (String) -> Unit = { rawNumber ->
        val clean = rawNumber.trim()
        if (clean.isNotBlank()) {
            val hasCall = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasCall || !hasCam) {
                pendingVideoNumber = clean
                videoPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.CALL_PHONE,
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO,
                    )
                )
            } else {
                // Always invoke placeCarrierVideoCall so the button works even if parent passed an empty lambda
                com.aether.call.data.CallLogRepository.placeCarrierVideoCall(context, clean)
                onVideoCallClick?.invoke(clean)
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showStorageDropdown by remember { mutableStateOf(false) }

    // Local reactive state for Star, Primary Phone, and Emergency SOS status
    var localStarred by remember(contact.id, contact.isStarred) { mutableStateOf(contact.isStarred) }
    val orderedPhones = remember(contact.phones) {
        mutableStateListOf<LabeledValue>().apply { addAll(contact.phones) }
    }
    var isEmergency by remember(contact.id) {
        mutableStateOf(ContactsRepository.isEmergencyContact(context, contact.id))
    }

    val availableAccounts = remember(context) { ContactsRepository.getAvailableStorageAccounts(context) }
    val selectedStorageKeys = remember(contact.linkedAccounts) {
        mutableStateListOf<String?>().apply {
            add(null) // Phone default
        }
    }

    val logsToRender = if (recentLogsForContact.isNotEmpty()) recentLogsForContact else allCallLogs
    val fullName = remember(contact) {
        listOf(contact.prefix, contact.firstName, contact.middleName, contact.lastName, contact.suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { contact.displayName.ifBlank { contact.primaryNumber } }
    }

    val primaryPhone = orderedPhones.firstOrNull()
    val primaryNumberString = primaryPhone?.value?.takeIf { it.isNotBlank() }
        ?: contact.primaryNumber.ifBlank { contact.number }
    val secondaryPhones = if (orderedPhones.size > 1) orderedPhones.drop(1) else emptyList()

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

                Spacer(modifier = Modifier.weight(1f))

                if (!isMyProfile) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val oldStarred = localStarred
                            localStarred = !oldStarred
                            onToggleStar(contact.copy(isStarred = oldStarred))
                        },
                    ) {
                        Icon(
                            imageVector = if (localStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favourite",
                            tint = if (localStarred) Color(0xFFFBBF24) else Color.White,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF000000),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFF1E2024), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isMyProfile) {
                            BottomBarPod(
                                icon = if (localStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                label = "Favourite",
                                iconTint = if (localStarred) Color(0xFFFBBF24) else Color.White,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val oldStarred = localStarred
                                    localStarred = !oldStarred
                                    onToggleStar(contact.copy(isStarred = oldStarred))
                                },
                            )
                        }
                        BottomBarPod(
                            icon = Icons.Default.Edit,
                            label = "Edit",
                            onClick = { onEditClick(contact.copy(phones = orderedPhones.toList(), isStarred = localStarred)) },
                        )
                        BottomBarPod(
                            icon = Icons.Default.QrCode,
                            label = "QR code",
                            onClick = { showQrSheet = true },
                        )
                        BottomBarPod(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = { onShareClick(contact) },
                        )
                        if (!isMyProfile) {
                            BottomBarPod(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                iconTint = Color(0xFFF43F5E),
                                labelColor = Color(0xFFF43F5E),
                                onClick = { showDeleteConfirm = true },
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. HERO CARD (Name, Primary Phone with Default Badge, Avatar, 3 Action Pods)
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF1E2024),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = fullName,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    if (isEmergency) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                        ) {
                                            Text(
                                                text = "SOS",
                                                color = Color(0xFFEF4444),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }

                                if (primaryPhone != null && primaryPhone.value.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${primaryPhone.label.ifBlank { "Mobile" }} ",
                                            fontSize = 14.sp,
                                            color = Color(0xFF9E9E9E),
                                        )
                                        Text(
                                            text = primaryPhone.value,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                        )
                                        if (orderedPhones.size > 1) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                            ) {
                                                Text(
                                                    text = "Default",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier.size(66.dp),
                            ) {
                                if (!contact.photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(contact.photoUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = fullName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = getContactInitial(contact),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 26.sp,
                                        )
                                    }
                                }
                            }
                        }

                        if (primaryNumberString.isNotBlank()) {
                            Spacer(modifier = Modifier.height(22.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Surface(
                                    onClick = { onCallClick(primaryNumberString) },
                                    shape = CircleShape,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }

                                Surface(
                                    onClick = { onMessageClick(primaryNumberString) },
                                    shape = CircleShape,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.ChatBubble, contentDescription = "Message", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Surface(
                                    onClick = { triggerVideoCall(primaryNumberString) },
                                    shape = CircleShape,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Videocam, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. SECONDARY PHONE NUMBERS WITH "Set as Default" PROMOTION
            secondaryPhones.forEachIndexed { secIdx, phone ->
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = phone.label.ifBlank { "Mobile" },
                                        fontSize = 12.sp,
                                        color = Color(0xFF9E9E9E),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = phone.value,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }

                                IconButton(onClick = { onCallClick(phone.value) }) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                }
                                IconButton(onClick = { onMessageClick(phone.value) }) {
                                    Icon(Icons.Default.ChatBubble, contentDescription = "SMS", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { triggerVideoCall(phone.value) }) {
                                    Icon(Icons.Default.Videocam, contentDescription = "Video", tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                }
                            }

                            // 1-Tap "Make Default / Primary Number" Button
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val actualIndex = secIdx + 1
                                    if (actualIndex in orderedPhones.indices) {
                                        val chosen = orderedPhones.removeAt(actualIndex)
                                        orderedPhones.add(0, chosen)
                                        coroutineScope.launch {
                                            ContactsRepository.setPrimaryPhoneNumber(context, contact.id, chosen.value)
                                            Toast.makeText(context, "${chosen.value} set as primary number", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Icon(Icons.Default.StarBorder, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set as primary / default number", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 3. WHATSAPP PILL ROW
            if (primaryPhone != null && primaryPhone.value.isNotBlank() && !isMyProfile) {
                item {
                    val e164 = PhoneNormalizer.toE164India(primaryPhone.value)
                    Surface(
                        onClick = {
                            if (e164.isNotBlank()) {
                                safeLaunchIntent(
                                    context,
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$e164")),
                                )
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "WhatsApp",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF25D366),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "WhatsApp",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. MULTIPLE EMAILS SECTION (Synced with Email Apps Open-With Chooser)
            if (contact.emails.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Email addresses", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9E9E9E))
                            contact.emails.forEachIndexed { idx, email ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launchEmailAppChooser(context, email.value) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = email.label.ifBlank { "Home" },
                                            fontSize = 12.sp,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = email.value,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Surface(
                                        onClick = { launchEmailAppChooser(context, email.value) },
                                        shape = CircleShape,
                                        color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Email, contentDescription = "Send Email", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                if (idx < contact.emails.lastIndex) {
                                    HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            // 5. ADDRESSES SECTION (Synced with Default Map Navigation)
            if (contact.addresses.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Locations & Addresses", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9E9E9E))
                            contact.addresses.forEachIndexed { idx, addr ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launchMapsNavigation(context, addr.value) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = addr.label.ifBlank { "Home" },
                                            fontSize = 12.sp,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = addr.value,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    Surface(
                                        onClick = { launchMapsNavigation(context, addr.value) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.Map, contentDescription = "Open Map", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Maps", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (idx < contact.addresses.lastIndex) {
                                    HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            // 6. BIRTHDAY & IMPORTANT DATES (With 1-Tap Calendar & Reminder Sync)
            if (contact.events.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Birthday & Important Dates", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9E9E9E))
                            contact.events.forEachIndexed { idx, evt ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Cake, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = evt.label.ifBlank { "Birthday" },
                                            fontSize = 12.sp,
                                            color = Color(0xFFFBBF24),
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = evt.value,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Surface(
                                        onClick = {
                                            syncBirthdayToCalendarWithReminder(
                                                context = context,
                                                contactName = fullName,
                                                eventLabel = evt.label,
                                                dateString = evt.value,
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFFBBF24).copy(alpha = 0.18f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.NotificationsActive, contentDescription = "Set Reminder", tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Set Reminder", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (idx < contact.events.lastIndex) {
                                    HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            // 7. EMERGENCY CONTACT (ICE / SOS) SYNC CARD
            if (!isMyProfile) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        border = if (isEmergency) BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)) else null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFEF4444).copy(alpha = 0.18f),
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Emergency, contentDescription = "Emergency SOS", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Emergency contact (ICE / SOS)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = if (isEmergency) "Synced to ICE Emergency Contacts group" else "Allow calling from Lockscreen SOS",
                                        fontSize = 12.sp,
                                        color = if (isEmergency) Color(0xFFEF4444) else Color(0xFF9E9E9E),
                                    )
                                }
                                Switch(
                                    checked = isEmergency,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isEmergency = checked
                                        coroutineScope.launch {
                                            ContactsRepository.setEmergencyContactStatus(context, contact.id, checked)
                                            Toast.makeText(
                                                context,
                                                if (checked) "Added to ICE Emergency Contacts" else "Removed from Emergency Contacts",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFEF4444),
                                    ),
                                )
                            }

                            if (isEmergency) {
                                TextButton(
                                    onClick = { openSystemEmergencySosSettings(context) },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("Open phone Emergency SOS settings →", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // 8. CENTERED "History" & MULTI-LOCATION "Storage locations" PILL DROPDOWN
            if (!isMyProfile) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            onClick = { showHistorySheet = true },
                            shape = CircleShape,
                            color = Color(0xFF2B2D33),
                            modifier = Modifier
                                .width(240.dp)
                                .height(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (logsToRender.isNotEmpty()) "History (${logsToRender.size})" else "History",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        // Multi-Location Storage Dropdown Pill
                        Box {
                            Surface(
                                onClick = { showStorageDropdown = true },
                                shape = CircleShape,
                                color = Color(0xFF2B2D33),
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(44.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Storage locations",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(18.dp))
                                }
                            }

                            DropdownMenu(
                                expanded = showStorageDropdown,
                                onDismissRequest = { showStorageDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF25272C))
                                    .width(280.dp),
                            ) {
                                Text(
                                    text = "Select storage locations (Multi-save)",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                availableAccounts.forEach { (label, key) ->
                                    val isChecked = selectedStorageKeys.contains(key)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981)),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(label, color = Color.White, fontSize = 14.sp)
                                            }
                                        },
                                        onClick = {
                                            if (isChecked && selectedStorageKeys.size > 1) {
                                                selectedStorageKeys.remove(key)
                                            } else if (!isChecked) {
                                                selectedStorageKeys.add(key)
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF3A3D45), thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Save to ${selectedStorageKeys.size} selected location(s)",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showStorageDropdown = false
                                        coroutineScope.launch {
                                            ContactsRepository.saveContactToMultipleAccounts(
                                                context = context,
                                                contact = contact.copy(phones = orderedPhones.toList(), isStarred = localStarred),
                                                selectedAccountKeys = selectedStorageKeys.toList(),
                                            )
                                            Toast.makeText(
                                                context,
                                                "Saved contact to ${selectedStorageKeys.size} storage location(s)",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // CALL HISTORY SHEET
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = Color(0xFF1E2024),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Call history • $fullName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                if (logsToRender.isEmpty()) {
                    Text(
                        text = "No call history with this contact",
                        fontSize = 14.sp,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    val groupedLogs = remember(logsToRender) {
                        logsToRender.groupBy { log ->
                            when {
                                DateUtils.isToday(log.dateMillis) -> "Today"
                                DateUtils.isToday(log.dateMillis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
                                else -> SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(log.dateMillis))
                            }
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groupedLogs.forEach { (dateHeader, dayLogs) ->
                            item {
                                Text(
                                    text = dateHeader,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            dayLogs.forEach { log ->
                                item {
                                    val (directionIcon, iconTint, label) = when (log.type) {
                                        CallLog.Calls.INCOMING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallReceived, Color(0xFF10B981), "Incoming call")
                                        CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMade, Color(0xFF38BDF8), "Outgoing call")
                                        else -> Triple(Icons.AutoMirrored.Filled.CallMissed, Color(0xFFF43F5E), "Missed call")
                                    }
                                    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.dateMillis))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(directionIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("$label • $timeText", color = Color.White, fontSize = 14.sp)
                                            Text("${log.number} • ${log.simDisplayName}", color = Color(0xFF8E8E93), fontSize = 12.sp)
                                        }
                                        Text("${log.durationSeconds}s", color = Color(0xFF8E8E93), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // QR CODE SHEET
    if (showQrSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQrSheet = false },
            containerColor = Color(0xFF1E2024),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = @Composable { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3D45)) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(fullName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Scan QR code to add contact", fontSize = 13.sp, color = Color(0xFF8E8E93), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                val vCardContent = remember(contact) { buildVCardString(contact) }
                val qrBitmap = remember(vCardContent) { generateQrCodeBitmap(vCardContent) }
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.size(240.dp).padding(12.dp)) {
                    androidx.compose.foundation.Image(bitmap = qrBitmap, contentDescription = "QR", modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF25272C),
            title = { Text("Delete contact?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Delete $fullName from your contacts?", color = Color(0xFF9E9E9E)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteClick(contact)
                }) {
                    Text("Delete", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
        )
    }
}

@Composable
private fun BottomBarPod(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = labelColor, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Authentic Samsung One UI Pill-Card Contact Editor with:
 * - Multi-Account Storage Location Dropdown at Top (Save to 1 or multiple accounts)
 * - Primary / Default Phone Number Star Selector
 * - Multiple Emails (Home, Work, Other)
 * - Multiple Addresses (Home, Work, Other)
 * - Birthday / Anniversary DatePicker + Calendar Reminder Sync
 * - Emergency Contact (ICE / SOS) Toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiContactEditSheet(
    contactToEdit: ContactItem?,
    prefilledNumber: String = "",
    onDismiss: () -> Unit,
    onSaveContact: (ContactItem) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val availableAccounts = remember(context) { ContactsRepository.getAvailableStorageAccounts(context) }
    val selectedStorageKeys = remember {
        mutableStateListOf<String?>(null)
    }
    var storageMenuExpanded by remember { mutableStateOf(false) }

    var photoUri by rememberSaveable { mutableStateOf(contactToEdit?.photoUri) }
    var callingCardPhotoUri by rememberSaveable { mutableStateOf(contactToEdit?.callingCardPhotoUri) }

    var prefix by rememberSaveable { mutableStateOf(contactToEdit?.prefix ?: "") }
    var firstName by rememberSaveable { mutableStateOf(contactToEdit?.firstName ?: (contactToEdit?.displayName ?: "")) }
    var middleName by rememberSaveable { mutableStateOf(contactToEdit?.middleName ?: "") }
    var lastName by rememberSaveable { mutableStateOf(contactToEdit?.lastName ?: "") }
    var suffix by rememberSaveable { mutableStateOf(contactToEdit?.suffix ?: "") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var isNameExpanded by rememberSaveable { mutableStateOf(false) }

    var primaryPhoneIndex by rememberSaveable { mutableStateOf(0) }
    var isEmergencyContact by rememberSaveable {
        mutableStateOf(contactToEdit?.let { ContactsRepository.isEmergencyContact(context, it.id) } ?: false)
    }

    val phonesList = remember {
        mutableStateListOf<LabeledValue>().apply {
            if (contactToEdit?.phones?.isNotEmpty() == true) {
                addAll(contactToEdit.phones)
            } else if (prefilledNumber.isNotBlank()) {
                add(LabeledValue(value = prefilledNumber, label = "Mobile"))
            } else {
                add(LabeledValue(value = "", label = "Mobile"))
            }
        }
    }

    val emailsList = remember {
        mutableStateListOf<LabeledValue>().apply {
            if (contactToEdit?.emails?.isNotEmpty() == true) {
                addAll(contactToEdit.emails)
            }
        }
    }

    val addressesList = remember {
        mutableStateListOf<LabeledValue>().apply {
            if (contactToEdit?.addresses?.isNotEmpty() == true) {
                addAll(contactToEdit.addresses)
            }
        }
    }

    val eventsList = remember {
        mutableStateListOf<LabeledValue>().apply {
            if (contactToEdit?.events?.isNotEmpty() == true) {
                addAll(contactToEdit.events)
            }
        }
    }

    var note by rememberSaveable { mutableStateOf(contactToEdit?.note ?: "") }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val key = (contactToEdit?.id?.takeIf { it > 0L }?.toString())
                ?: phonesList.firstOrNull()?.value
                ?: System.currentTimeMillis().toString()
            val (permanentUri, _) = ContactsRepository.persistPhotoToInternalStorage(context, uri.toString(), key)
            photoUri = permanentUri ?: uri.toString()
        }
    }

    val callingCardPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callingCardPhotoUri = uri.toString()
    }

    val phoneLabelOptions = listOf("Mobile", "Work", "Home", "Main", "Other")
    val emailLabelOptions = listOf("Home", "Work", "Other")
    val addressLabelOptions = listOf("Home", "Work", "Other")
    val eventLabelOptions = listOf("Birthday", "Anniversary", "Other")

    val openDatePicker: (Int) -> Unit = { targetIndex ->
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val dateFormatted = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                if (targetIndex in eventsList.indices) {
                    eventsList[targetIndex] = eventsList[targetIndex].copy(value = dateFormatted)
                } else {
                    eventsList.add(LabeledValue(value = dateFormatted, label = "Birthday"))
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    Scaffold(
        containerColor = Color(0xFF000000),
        bottomBar = {
            Surface(
                color = Color(0xFF000000),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val combinedName = listOf(prefix, firstName, middleName, lastName, suffix)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { firstName.ifBlank { phonesList.firstOrNull()?.value ?: "Unnamed" } }

                            // Put the chosen Primary/Default phone at index 0
                            val validPhones = phonesList.filter { it.value.isNotBlank() }.toMutableList()
                            if (primaryPhoneIndex in validPhones.indices && primaryPhoneIndex > 0) {
                                val primaryItem = validPhones.removeAt(primaryPhoneIndex)
                                validPhones.add(0, primaryItem)
                            }

                            val guaranteedPhotoUri = if (!photoUri.isNullOrBlank()) {
                                ContactsRepository.saveContactPhotoGuaranteed(
                                    context = context,
                                    contactId = contactToEdit?.id ?: 0L,
                                    primaryNumber = validPhones.firstOrNull()?.value.orEmpty(),
                                    sourceUriString = photoUri,
                                ) ?: photoUri
                            } else {
                                null
                            }

                            val newContact = ContactItem(
                                id = contactToEdit?.id ?: 0L,
                                lookupKey = contactToEdit?.lookupKey ?: "",
                                displayName = combinedName,
                                prefix = prefix.trim(),
                                firstName = firstName.trim(),
                                middleName = middleName.trim(),
                                lastName = lastName.trim(),
                                suffix = suffix.trim(),
                                phones = validPhones,
                                emails = emailsList.filter { it.value.isNotBlank() },
                                addresses = addressesList.filter { it.value.isNotBlank() },
                                events = eventsList.filter { it.value.isNotBlank() },
                                websites = contactToEdit?.websites ?: emptyList(),
                                relationships = contactToEdit?.relationships ?: emptyList(),
                                note = note.trim(),
                                photoUri = guaranteedPhotoUri,
                                callingCardPhotoUri = callingCardPhotoUri,
                                isStarred = contactToEdit?.isStarred ?: false,
                                linkedAccounts = availableAccounts
                                    .filter { selectedStorageKeys.contains(it.second) }
                                    .map { it.first },
                            )

                            coroutineScope.launch {
                                val targetContactId = if (contactToEdit == null || contactToEdit.id <= 0L) newContact.id else contactToEdit.id
                                if (targetContactId > 0L) {
                                    if (validPhones.isNotEmpty()) {
                                        ContactsRepository.setPrimaryPhoneNumber(context, targetContactId, validPhones.first().value)
                                    }
                                    ContactsRepository.setEmergencyContactStatus(context, targetContactId, isEmergencyContact)

                                    if (!photoUri.isNullOrBlank()) {
                                        ContactsRepository.saveContactPhotoGuaranteed(context, targetContactId, newContact.primaryNumber, photoUri)
                                        context.contentResolver.query(
                                            android.provider.ContactsContract.RawContacts.CONTENT_URI,
                                            arrayOf(android.provider.ContactsContract.RawContacts._ID),
                                            "${android.provider.ContactsContract.RawContacts.CONTACT_ID} = ?",
                                            arrayOf(targetContactId.toString()),
                                            null,
                                        )?.use { rc ->
                                            if (rc.moveToFirst()) {
                                                val rawId = rc.getLong(0)
                                                ContactsRepository.saveContactPhotoToDatabase(context, rawId, photoUri)
                                            }
                                        }
                                    }
                                }
                                if (selectedStorageKeys.size > 1) {
                                    ContactsRepository.saveContactToMultipleAccounts(
                                        context = context,
                                        contact = newContact,
                                        selectedAccountKeys = selectedStorageKeys.drop(1),
                                    )
                                }
                            }

                            onSaveContact(newContact)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save", color = Color(0xFF10B981), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 0. SAVE LOCATION MULTI-ACCOUNT DROPDOWN BAR
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { storageMenuExpanded = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Save contact to", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                val summary = availableAccounts
                                    .filter { selectedStorageKeys.contains(it.second) }
                                    .joinToString(", ") { it.first }
                                    .ifBlank { "Phone (Device)" }
                                Text(summary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF9E9E9E))
                        }
                    }

                    DropdownMenu(
                        expanded = storageMenuExpanded,
                        onDismissRequest = { storageMenuExpanded = false },
                        modifier = Modifier
                            .background(Color(0xFF25272C))
                            .fillMaxWidth(0.9f),
                    ) {
                        Text(
                            text = "Select one or multiple storage locations",
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        availableAccounts.forEach { (label, key) ->
                            val isChecked = selectedStorageKeys.contains(key)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = if (isChecked) Color(0xFF10B981) else Color(0xFF9E9E9E),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(label, color = Color.White, fontSize = 14.sp)
                                    }
                                },
                                onClick = {
                                    if (isChecked && selectedStorageKeys.size > 1) {
                                        selectedStorageKeys.remove(key)
                                    } else if (!isChecked) {
                                        selectedStorageKeys.add(key)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 1. TOP PROFILE CARD BANNER
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF282A30),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!callingCardPhotoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = callingCardPhotoUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Surface(
                            onClick = {
                                callingCardPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = CircleShape,
                            color = Color(0xFF1E2024).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFF4B5563)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.ContactPage, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create profile card", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(14.dp)
                                .size(68.dp)
                                .clickable {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                        ) {
                            Surface(shape = CircleShape, color = Color(0xFFFBBF24), modifier = Modifier.fillMaxSize()) {
                                if (!photoUri.isNullOrBlank()) {
                                    AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    val currentTempContact = ContactItem(
                                        firstName = firstName,
                                        displayName = firstName.ifBlank { contactToEdit?.displayName ?: "" },
                                    )
                                    val editInitial = getContactInitial(currentTempContact)
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = editInitial,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF3A3D45),
                                border = BorderStroke(1.5.dp, Color(0xFF1E2024)),
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit photo", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 2. NAME PILL CARD
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            OneUiBareTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                placeholder = "Name",
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { isNameExpanded = !isNameExpanded }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = if (isNameExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand name",
                                    tint = Color(0xFF9E9E9E),
                                )
                            }
                        }

                        if (isNameExpanded) {
                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp, top = 10.dp, bottom = 10.dp))
                            Column(modifier = Modifier.padding(start = 34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OneUiBareTextField(value = prefix, onValueChange = { prefix = it }, placeholder = "Prefix (Mr., Dr.)")
                                OneUiBareTextField(value = middleName, onValueChange = { middleName = it }, placeholder = "Middle name")
                                OneUiBareTextField(value = lastName, onValueChange = { lastName = it }, placeholder = "Last name / Surname")
                                OneUiBareTextField(value = suffix, onValueChange = { suffix = it }, placeholder = "Suffix")
                                OneUiBareTextField(value = nickname, onValueChange = { nickname = it }, placeholder = "Nickname")
                            }
                        }
                    }
                }
            }

            // 3. PHONE NUMBERS PILL CARD (With Primary/Default Radio Star when >1 phone)
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        phonesList.forEachIndexed { idx, phoneItem ->
                            var labelMenuOpen by remember { mutableStateOf(false) }
                            val isPrimary = idx == primaryPhoneIndex

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (idx == 0) {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(20.dp))
                                } else {
                                    Spacer(modifier = Modifier.size(20.dp))
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            Row(
                                                modifier = Modifier.clickable { labelMenuOpen = true },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(phoneItem.label.ifBlank { "Mobile" }, color = Color(0xFF9E9E9E), fontSize = 13.sp)
                                                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(
                                                expanded = labelMenuOpen,
                                                onDismissRequest = { labelMenuOpen = false },
                                                modifier = Modifier.background(Color(0xFF25272C)),
                                            ) {
                                                phoneLabelOptions.forEach { opt ->
                                                    DropdownMenuItem(
                                                        text = { Text(opt, color = Color.White) },
                                                        onClick = {
                                                            phonesList[idx] = phoneItem.copy(label = opt)
                                                            labelMenuOpen = false
                                                        },
                                                    )
                                                }
                                            }
                                        }

                                        if (phonesList.size > 1) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Surface(
                                                onClick = { primaryPhoneIndex = idx },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isPrimary) Color(0xFF10B981).copy(alpha = 0.22f) else Color(0xFF2A2D35),
                                            ) {
                                                Text(
                                                    text = if (isPrimary) "★ Default" else "Set default",
                                                    color = if (isPrimary) Color(0xFF10B981) else Color(0xFF9E9E9E),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    OneUiBareTextField(
                                        value = phoneItem.value,
                                        onValueChange = { phonesList[idx] = phoneItem.copy(value = it) },
                                        placeholder = "Phone number",
                                        keyboardType = KeyboardType.Phone,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (phonesList.size > 1) {
                                            phonesList.removeAt(idx)
                                            if (primaryPhoneIndex >= phonesList.size) primaryPhoneIndex = 0
                                        } else {
                                            phonesList[0] = phoneItem.copy(value = "")
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }

                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp, top = 10.dp, bottom = 10.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { phonesList.add(LabeledValue(value = "", label = "Mobile")) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Add phone number", color = Color(0xFF10B981), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 4. MULTIPLE EMAILS PILL CARD
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        emailsList.forEachIndexed { idx, emailItem ->
                            var emailMenuOpen by remember { mutableStateOf(false) }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (idx == 0) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                } else {
                                    Spacer(modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box {
                                        Row(modifier = Modifier.clickable { emailMenuOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                                            Text(emailItem.label.ifBlank { "Home" }, color = Color(0xFF9E9E9E), fontSize = 12.sp)
                                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(15.dp))
                                        }
                                        DropdownMenu(expanded = emailMenuOpen, onDismissRequest = { emailMenuOpen = false }, modifier = Modifier.background(Color(0xFF25272C))) {
                                            emailLabelOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt, color = Color.White) },
                                                    onClick = {
                                                        emailsList[idx] = emailItem.copy(label = opt)
                                                        emailMenuOpen = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OneUiBareTextField(
                                        value = emailItem.value,
                                        onValueChange = { emailsList[idx] = emailItem.copy(value = it) },
                                        placeholder = "Email address",
                                        keyboardType = KeyboardType.Email,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                IconButton(onClick = { emailsList.removeAt(idx) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove", tint = Color(0xFFEF4444))
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp, top = 8.dp, bottom = 8.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { emailsList.add(LabeledValue("", label = if (emailsList.isEmpty()) "Home" else "Work")) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Add email address", color = Color(0xFF38BDF8), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 5. MULTIPLE ADDRESSES PILL CARD (Synced with Maps)
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        addressesList.forEachIndexed { idx, addrItem ->
                            var addrMenuOpen by remember { mutableStateOf(false) }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (idx == 0) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                } else {
                                    Spacer(modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box {
                                        Row(modifier = Modifier.clickable { addrMenuOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                                            Text(addrItem.label.ifBlank { "Home" }, color = Color(0xFF9E9E9E), fontSize = 12.sp)
                                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(15.dp))
                                        }
                                        DropdownMenu(expanded = addrMenuOpen, onDismissRequest = { addrMenuOpen = false }, modifier = Modifier.background(Color(0xFF25272C))) {
                                            addressLabelOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt, color = Color.White) },
                                                    onClick = {
                                                        addressesList[idx] = addrItem.copy(label = opt)
                                                        addrMenuOpen = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OneUiBareTextField(
                                        value = addrItem.value,
                                        onValueChange = { addressesList[idx] = addrItem.copy(value = it) },
                                        placeholder = "Street, City, Pin code",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                IconButton(onClick = { addressesList.removeAt(idx) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove", tint = Color(0xFFEF4444))
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp, top = 8.dp, bottom = 8.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addressesList.add(LabeledValue("", label = "Home")) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Add address (Maps sync)", color = Color(0xFF10B981), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 6. BIRTHDAY & IMPORTANT DATES PILL CARD (With Calendar Reminder Sync)
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        eventsList.forEachIndexed { idx, evItem ->
                            var evMenuOpen by remember { mutableStateOf(false) }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cake, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box {
                                        Row(modifier = Modifier.clickable { evMenuOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                                            Text(evItem.label.ifBlank { "Birthday" }, color = Color(0xFFFBBF24), fontSize = 12.sp)
                                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(15.dp))
                                        }
                                        DropdownMenu(expanded = evMenuOpen, onDismissRequest = { evMenuOpen = false }, modifier = Modifier.background(Color(0xFF25272C))) {
                                            eventLabelOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt, color = Color.White) },
                                                    onClick = {
                                                        eventsList[idx] = evItem.copy(label = opt)
                                                        evMenuOpen = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = evItem.value.ifBlank { "Tap to pick date" },
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { openDatePicker(idx) },
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        syncBirthdayToCalendarWithReminder(
                                            context = context,
                                            contactName = firstName.ifBlank { "Contact" },
                                            eventLabel = evItem.label,
                                            dateString = evItem.value,
                                        )
                                    },
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Sync to Calendar", tint = Color(0xFFFBBF24))
                                }
                                IconButton(onClick = { eventsList.removeAt(idx) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove", tint = Color(0xFFEF4444))
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp, top = 8.dp, bottom = 8.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDatePicker(eventsList.size) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Add birthday / important date", color = Color(0xFFFBBF24), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 7. EMERGENCY CONTACT (SOS) PILL CARD
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mark as Emergency Contact (SOS)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("Syncs with ICE Emergency group", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                        }
                        Switch(
                            checked = isEmergencyContact,
                            onCheckedChange = { isEmergencyContact = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFEF4444),
                            ),
                        )
                    }
                }
            }

            // 8. NOTES PILL CARD
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1E2024), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Note, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        OneUiBareTextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = "Notes",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneUiBareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(Color(0xFF10B981)),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF9E9E9E),
                        fontSize = 15.sp,
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier,
    )
}
