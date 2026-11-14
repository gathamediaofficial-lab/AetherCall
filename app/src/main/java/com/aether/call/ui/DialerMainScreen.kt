package com.aether.call.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.call.data.CallLogItem
import com.aether.call.data.ContactItem
import com.aether.call.data.LabeledValue
import com.aether.call.util.PhoneNormalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerMainScreen(
    viewModel: DialerViewModel = viewModel(),
    onRequestDefaultDialer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val currentTab by viewModel.currentTab.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val starredContacts by viewModel.starredContacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val enteredNumber by viewModel.enteredNumber.collectAsState()
    val quickReplies by viewModel.quickReplies.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val myProfile by viewModel.myProfile.collectAsState()
    val simSelectionDialog by viewModel.simSelectionDialog.collectAsState()
    val activeSims by viewModel.activeSims.collectAsState()

    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var showAboutScreen by rememberSaveable { mutableStateOf(false) }
    var isQuickRepliesOpen by rememberSaveable { mutableStateOf(false) }
    var isRecordingsOpen by rememberSaveable { mutableStateOf(false) }
    var isMyProfileCardOpen by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isMissedFilterActive by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var selectedContactForDetails by remember { mutableStateOf<ContactItem?>(null) }
    var contactBeingEdited by remember { mutableStateOf<ContactItem?>(null) }
    var isCreatingContact by remember { mutableStateOf(false) }
    var prefilledNewNumber by remember { mutableStateOf("") }

    var showClearLogsConfirm by remember { mutableStateOf(false) }
    var showGroupsInfoDialog by remember { mutableStateOf(false) }

    BackHandler(
        enabled = showAboutScreen || isMyProfileCardOpen || isRecordingsOpen || isQuickRepliesOpen || isSettingsOpen || isSearchActive ||
                selectedContactForDetails != null || isCreatingContact || contactBeingEdited != null ||
                enteredNumber.isNotEmpty(),
    ) {
        when {
            showAboutScreen -> showAboutScreen = false
            isMyProfileCardOpen -> isMyProfileCardOpen = false
            selectedContactForDetails != null -> selectedContactForDetails = null
            isCreatingContact || contactBeingEdited != null -> {
                isCreatingContact = false
                contactBeingEdited = null
                prefilledNewNumber = ""
            }
            isRecordingsOpen -> isRecordingsOpen = false
            isQuickRepliesOpen -> isQuickRepliesOpen = false
            isSettingsOpen -> isSettingsOpen = false
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            enteredNumber.isNotEmpty() -> viewModel.clearEnteredNumber()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkDefaultDialerRole(context)
        viewModel.loadRecentCalls(context)
        viewModel.loadContacts(context)
        viewModel.loadBlockedNumbers(context)
        viewModel.observeStarredContacts(context)
        viewModel.loadQuickReplies(context)
        viewModel.loadAppSettings(context)
        viewModel.loadMyProfile(context)
        viewModel.loadActiveSims(context)
    }

    val filteredContacts = remember(contacts, isSearchActive, searchQuery, userSettings.hideContactsWithoutNumbers) {
        val baseList = if (userSettings.hideContactsWithoutNumbers) {
            contacts.filter { it.phones.any { p -> p.value.isNotBlank() } }
        } else {
            contacts
        }
        if (isSearchActive && searchQuery.isNotBlank()) {
            baseList.filter { contact ->
                contact.displayName.contains(searchQuery, ignoreCase = true) ||
                        contact.phones.any { p -> p.value.contains(searchQuery) }
            }
        } else {
            baseList
        }
    }

    val filteredCallLogs = remember(callLogs, isSearchActive, searchQuery) {
        if (isSearchActive && searchQuery.isNotBlank()) {
            callLogs.filter {
                (it.name?.contains(searchQuery, ignoreCase = true) == true) || it.number.contains(searchQuery)
            }
        } else {
            callLogs
        }
    }

    val currentSelectedContact = remember(selectedContactForDetails, contacts) {
        val target = selectedContactForDetails ?: return@remember null
        val matched = if (target.id > 0L) {
            contacts.find { it.id == target.id }
        } else {
            val norm = PhoneNormalizer.normalizeDigits(target.primaryNumber)
            if (norm.isNotEmpty()) {
                contacts.find { c -> c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == norm } }
            } else {
                null
            }
        }
        matched ?: target
    }

    val recentLogsForContact = remember(currentSelectedContact, callLogs) {
        val targetContact = currentSelectedContact ?: return@remember emptyList<CallLogItem>()
        val phoneNormSet = targetContact.phones
            .map { PhoneNormalizer.normalizeDigits(it.value) }
            .filter { it.isNotBlank() }
            .toSet()
        if (phoneNormSet.isEmpty()) {
            emptyList()
        } else {
            callLogs.filter { log ->
                val logNorm = PhoneNormalizer.normalizeDigits(log.number)
                logNorm.isNotEmpty() && (phoneNormSet.contains(logNorm) ||
                        phoneNormSet.any { p -> logNorm.endsWith(p) || p.endsWith(logNorm) })
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF000000),
    ) {
        when {
            // ROUTE -1: LIVE MY PROFILE CARD
            isMyProfileCardOpen -> {
                OneUiContactCardScreen(
                    contact = myProfile,
                    isMyProfile = true,
                    onBack = { isMyProfileCardOpen = false },
                    onCallClick = { num ->
                        if (num.isNotBlank()) viewModel.placeOutgoingCall(context, num)
                    },
                    onMessageClick = { num ->
                        if (num.isNotBlank()) viewModel.launchSms(context, num)
                    },
                    onVideoCallClick = { num ->
                        if (num.isNotBlank()) viewModel.placeOutgoingCall(context, num)
                    },
                    onToggleStar = {},
                    onEditClick = {
                        val currentProfile = myProfile
                        isMyProfileCardOpen = false
                        contactBeingEdited = currentProfile
                    },
                    onShareClick = { target ->
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${target.displayName}: ${target.primaryNumber}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share My Profile"))
                        } catch (_: Exception) {
                        }
                    },
                    onDeleteClick = {},
                )
            }

            // ROUTE 0: FULL-SCREEN ONE UI CONTACT CARD
            currentSelectedContact != null -> {
                OneUiContactCardScreen(
                    contact = currentSelectedContact,
                    recentLogsForContact = recentLogsForContact,
                    onBack = { selectedContactForDetails = null },
                    onCallClick = { num ->
                        selectedContactForDetails = null
                        viewModel.placeOutgoingCall(context, num)
                    },
                    onMessageClick = { num ->
                        viewModel.launchSms(context, num)
                    },
                    onVideoCallClick = { num ->
                        try {
                            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).apply {
                                putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            viewModel.placeOutgoingCall(context, num)
                        }
                    },
                    onToggleStar = { target ->
                        val toggled = target.copy(isStarred = !target.isStarred)
                        selectedContactForDetails = toggled
                        viewModel.toggleStar(context, target)
                    },
                    onEditClick = { target ->
                        selectedContactForDetails = null
                        contactBeingEdited = target
                    },
                    onShareClick = { target ->
                        try {
                            val phonesSummary = target.phones.joinToString("\n") { "${it.label}: ${it.value}" }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${target.displayName}\n$phonesSummary")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
                        } catch (_: Exception) {
                        }
                    },
                    onDeleteClick = { target ->
                        viewModel.deleteContact(context, target)
                        selectedContactForDetails = null
                    },
                )
            }

            // ROUTE 0.5: RECORDED CALLS PLAYBACK SCREEN
            isRecordingsOpen -> {
                OneUiRecordingsScreen(
                    onNavigateBack = { isRecordingsOpen = false },
                )
            }

            // ROUTE 1: QUICK DECLINE MESSAGES EDITOR
            isQuickRepliesOpen -> {
                QuickRepliesSettingsScreen(
                    templates = quickReplies,
                    onAddTemplate = { viewModel.addQuickReply(context, it) },
                    onUpdateTemplate = { t, text -> viewModel.updateQuickReply(context, t, text) },
                    onDeleteTemplate = { viewModel.deleteQuickReply(context, it) },
                    onNavigateBack = { isQuickRepliesOpen = false },
                )
            }

            // ROUTE 2: CALL SETTINGS
            isSettingsOpen -> {
                OneUiSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { isSettingsOpen = false },
                    onOpenQuickReplies = { isQuickRepliesOpen = true },
                    onOpenRecordings = { isRecordingsOpen = true },
                )
            }

            // ROUTE 2.5: ABOUT AETHER CALL
            showAboutScreen -> {
                AboutSettingsScreen(
                    appVersionName = "1.0.0",
                    onBack = { showAboutScreen = false },
                )
            }

            // ROUTE 3: MAIN DIALER SCAFFOLD (DEFAULT)
            else -> {
                Scaffold(
                    modifier = modifier.fillMaxSize(),
                    containerColor = Color(0xFF000000),
                    topBar = {
                        OneUiTopHeader(
                            currentTab = currentTab,
                            contactsCount = filteredContacts.size,
                            isSearchActive = isSearchActive,
                            searchQuery = searchQuery,
                            isMissedFilterActive = isMissedFilterActive,
                            onQueryChange = { searchQuery = it },
                            onToggleSearch = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            onAddContactClick = {
                                prefilledNewNumber = ""
                                isCreatingContact = true
                            },
                            onFilterClick = { isMissedFilterActive = !isMissedFilterActive },
                            onOverflowMenuClick = { showOverflowMenu = true },
                            showOverflowMenu = showOverflowMenu,
                            onDismissOverflowMenu = { showOverflowMenu = false },
                            overflowMenuContent = {
                                when (currentTab) {
                                    NavigationTab.KEYPAD -> {
                                        DropdownMenuItem(
                                            text = { Text("Speed dial numbers", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                viewModel.selectTab(NavigationTab.CONTACTS)
                                            },
                                        )
                                        HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                        DropdownMenuItem(
                                            text = { Text("Settings", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                isSettingsOpen = true
                                            },
                                        )
                                    }
                                    NavigationTab.RECENTS -> {
                                        DropdownMenuItem(
                                            text = { Text(if (isMissedFilterActive) "Show all calls" else "Show missed calls", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                isMissedFilterActive = !isMissedFilterActive
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Clear call history", color = Color(0xFFF43F5E)) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showClearLogsConfirm = true
                                            },
                                        )
                                        HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                        DropdownMenuItem(
                                            text = { Text("Settings", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                isSettingsOpen = true
                                            },
                                        )
                                    }
                                    NavigationTab.CONTACTS -> {
                                        DropdownMenuItem(
                                            text = { Text("Create new contact", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                prefilledNewNumber = ""
                                                isCreatingContact = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Groups", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showGroupsInfoDialog = true
                                            },
                                        )
                                        HorizontalDivider(color = Color(0xFF2E3138), thickness = 0.5.dp)
                                        DropdownMenuItem(
                                            text = { Text("Settings", color = Color.White) },
                                            onClick = {
                                                showOverflowMenu = false
                                                isSettingsOpen = true
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        OneUiBottomNavigation(
                            selectedTab = currentTab,
                            onTabSelected = { tab ->
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isSearchActive = false
                                searchQuery = ""
                                viewModel.selectTab(tab)
                            },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (currentTab) {
                            NavigationTab.KEYPAD -> {
                                OneUiKeypadScreen(
                                    enteredNumber = enteredNumber,
                                    allContacts = contacts,
                                    keypadToneMode = userSettings.keypadToneMode,
                                    onDigitPress = { viewModel.appendDigit(it.toString()) },
                                    onDeletePress = { viewModel.deleteLastDigit() },
                                    onClearAll = { viewModel.clearEnteredNumber() },
                                    onCallClick = { number -> viewModel.placeOutgoingCall(context, number) },
                                    onAddToContactClick = { number ->
                                        prefilledNewNumber = number
                                        isCreatingContact = true
                                    },
                                )
                            }
                            NavigationTab.RECENTS -> {
                                OneUiRecentsScreen(
                                    callLogs = filteredCallLogs,
                                    isMissedFilterActive = isMissedFilterActive,
                                    onClearMissedFilter = { isMissedFilterActive = false },
                                    onCallClick = { number -> viewModel.placeOutgoingCall(context, number) },
                                    onMessageClick = { number -> viewModel.launchSms(context, number) },
                                    onCallDetailsClick = { log ->
                                        val norm = PhoneNormalizer.normalizeDigits(log.number)
                                        val matched = contacts.find { c ->
                                            c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == norm }
                                        } ?: ContactItem(
                                            id = 0L,
                                            lookupKey = "",
                                            displayName = log.name?.takeIf { it.isNotBlank() } ?: log.number,
                                            phones = listOf(
                                                LabeledValue(
                                                    value = log.number,
                                                    type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                                    label = "Mobile",
                                                ),
                                            ),
                                        )
                                        selectedContactForDetails = matched
                                    },
                                )
                            }
                            NavigationTab.CONTACTS -> {
                                OneUiContactsScreen(
                                    myProfile = myProfile,
                                    favoriteContacts = starredContacts,
                                    allContacts = filteredContacts,
                                    searchQuery = if (isSearchActive) searchQuery else "",
                                    onMyProfileClick = { isMyProfileCardOpen = true },
                                    onContactClick = { contact -> selectedContactForDetails = contact },
                                    onCallContact = { number -> viewModel.placeOutgoingCall(context, number) },
                                    onMessageContact = { number -> viewModel.launchSms(context, number) },
                                    onGroupsClick = { showGroupsInfoDialog = true },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full Multi-Field Contact Create / Edit Sheet
        if (isCreatingContact || contactBeingEdited != null) {
            OneUiContactEditSheet(
                contactToEdit = contactBeingEdited,
                prefilledNumber = prefilledNewNumber,
                onDismiss = {
                    isCreatingContact = false
                    contactBeingEdited = null
                    prefilledNewNumber = ""
                },
                onSaveContact = { contact ->
                    if (contact.id == -1L || contactBeingEdited?.id == -1L) {
                        viewModel.updateMyProfile(context, contact)
                        isMyProfileCardOpen = true
                    } else {
                        viewModel.saveFullContact(context, contact)
                        selectedContactForDetails = contact
                    }
                    isCreatingContact = false
                    contactBeingEdited = null
                    prefilledNewNumber = ""
                },
            )
        }

        // Dual-SIM Selection Dialog
        simSelectionDialog?.let { pendingCall ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSimSelectionDialog() },
                containerColor = Color(0xFF1E2024),
                shape = RoundedCornerShape(26.dp),
                title = {
                    Column {
                        Text(
                            text = "Select SIM card",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = pendingCall.contactName ?: pendingCall.number,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        activeSims.forEachIndexed { idx, sim ->
                            Surface(
                                onClick = {
                                    viewModel.selectSimAndMakeCall(context, pendingCall.number, sim.slotIndex)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF141619),
                                border = BorderStroke(1.dp, Color(0xFF2E3138)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SimCard,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "SIM ${idx + 1} • ${sim.carrierName}",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Slot ${sim.slotIndex + 1}",
                                            color = Color(0xFF8E8E93),
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSimSelectionDialog() }) {
                        Text("Cancel", color = Color(0xFF8E8E93))
                    }
                },
            )
        }

        // Clear Call History Confirmation Dialog
        if (showClearLogsConfirm) {
            AlertDialog(
                onDismissRequest = { showClearLogsConfirm = false },
                containerColor = Color(0xFF1E2024),
                title = {
                    Text(
                        text = "Clear call history?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "All recent call logs will be cleared from this app.",
                        color = Color(0xFF8E8E93),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearLogsConfirm = false
                            viewModel.clearAllCallLogs(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                    ) {
                        Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearLogsConfirm = false }) {
                        Text("Cancel", color = Color(0xFF8E8E93))
                    }
                },
            )
        }

        // Groups Info Dialog
        if (showGroupsInfoDialog) {
            AlertDialog(
                onDismissRequest = { showGroupsInfoDialog = false },
                containerColor = Color(0xFF1E2024),
                title = {
                    Text(
                        text = "Contact Groups",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Favourites: ${starredContacts.size} contacts",
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "All Synced Contacts: ${contacts.size} contacts",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showGroupsInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
    }
}