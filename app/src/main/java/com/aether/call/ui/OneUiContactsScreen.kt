package com.aether.call.ui

import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.aether.call.data.getContactInitial
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aether.call.data.ContactItem
import kotlinx.coroutines.launch

/**
 * Highlights matching search substrings in Samsung One UI Emerald Green (#10B981).
 */
fun buildHighlightedText(
    fullText: String,
    query: String,
    highlightColor: Color = Color(0xFF10B981),
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(fullText)
    return buildAnnotatedString {
        var startIndex = 0
        val lowerText = fullText.lowercase()
        val lowerQuery = query.trim().lowercase()
        while (startIndex < fullText.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (matchIndex == -1) {
                append(fullText.substring(startIndex))
                break
            }
            if (matchIndex > startIndex) {
                append(fullText.substring(startIndex, matchIndex))
            }
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                append(fullText.substring(matchIndex, matchIndex + lowerQuery.length))
            }
            startIndex = matchIndex + lowerQuery.length
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OneUiContactsScreen(
    myProfile: ContactItem = ContactItem(displayName = "Mr. Naresh Kumar Meena"),
    favoriteContacts: List<ContactItem> = emptyList(),
    allContacts: List<ContactItem>,
    searchQuery: String = "",
    onMyProfileClick: () -> Unit = {},
    onContactClick: (ContactItem) -> Unit,
    onCallContact: (String) -> Unit,
    onMessageContact: ((String) -> Unit)? = null,
    onGroupsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var expandedContactId by rememberSaveable { mutableStateOf<Long?>(null) }

    val handleSwipeMessage: (String) -> Unit = { number ->
        if (onMessageContact != null) {
            onMessageContact(number)
        } else if (number.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    var pendingVideoNumber by remember { mutableStateOf<String?>(null) }
    val videoPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val target = pendingVideoNumber
        pendingVideoNumber = null
        if (perms[android.Manifest.permission.CALL_PHONE] == true &&
            perms[android.Manifest.permission.CAMERA] == true &&
            !target.isNullOrBlank()
        ) {
            com.aether.call.data.CallLogRepository.placeCarrierVideoCall(context, target)
        }
    }

    val handleVideoCall: (String) -> Unit = { number ->
        val clean = number.trim()
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
                com.aether.call.data.CallLogRepository.placeCarrierVideoCall(context, clean)
            }
        }
    }

    val groupedContacts = remember(allContacts) {
        allContacts.groupBy { contact ->
            val firstStr = getContactInitial(contact)
            val first = firstStr.firstOrNull() ?: '#'
            if (first.isLetter()) first else '#'
        }.toSortedMap(compareBy { if (it == '#') ' ' else it })
    }

    val sectionIndexMap = remember(groupedContacts, favoriteContacts) {
        val map = mutableMapOf<Char, Int>()
        var currentIndex = 3 // My Profile + Favourites + Groups
        groupedContacts.forEach { (letter, _) ->
            map[letter] = currentIndex
            currentIndex += 2
        }
        map
    }

    val pastelColors = remember {
        listOf(
            Color(0xFFF59E0B),
            Color(0xFF38BDF8),
            Color(0xFF10B981),
            Color(0xFFEC4899),
            Color(0xFFA855F7),
            Color(0xFF3B82F6),
        )
    }

    val scrubberLetters = remember { listOf('#') + ('A'..'Z').toList() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // IF SEARCH QUERY IS ACTIVE -> RENDER UNIFIED SEARCH RESULT CARD ("Contacts  6 found ^")
            if (searchQuery.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Contacts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8E8E93),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${allContacts.size} found",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8E8E93),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            allContacts.forEachIndexed { index, contact ->
                                val colorIdx = (contact.id.hashCode() and 0x7FFFFFFF) % pastelColors.size
                                val avatarColor = pastelColors[colorIdx]
                                val isExpanded = expandedContactId == contact.id

                                OneUiExpandableContactRow(
                                    contact = contact,
                                    avatarColor = avatarColor,
                                    searchQuery = searchQuery,
                                    showSubtitleInCollapsed = true,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedContactId = if (isExpanded) null else contact.id
                                    },
                                    onCallClick = onCallContact,
                                    onMessageClick = handleSwipeMessage,
                                    onVideoClick = handleVideoCall,
                                    onInfoClick = { onContactClick(contact) },
                                )

                                if (index < allContacts.lastIndex) {
                                    HorizontalDivider(
                                        color = Color(0xFF2E3138),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // SECTION 1: MY PROFILE (Matches 25619.jpg)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "My profile",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8E8E93),
                            ),
                            modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
                        )

                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMyProfileClick()
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF0284C7), CircleShape)
                                        .clip(CircleShape),
                                ) {
                                    if (!myProfile.photoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(myProfile.photoUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = myProfile.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        val initial = getContactInitial(myProfile)
                                        Text(
                                            text = initial,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = myProfile.displayName.ifBlank { "Mr. Naresh Kumar Meena" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // SECTION 2: FAVOURITES OR "Add your favourite contacts" PILL (Matches 25619.jpg)
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (favoriteContacts.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .clickable { onGroupsClick() },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFFFBBF24), CircleShape),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Add your favourite contacts",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "★ Favourites",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8E8E93),
                                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
                            )

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF1E2024),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    favoriteContacts.forEachIndexed { index, contact ->
                                        val colorIdx = (contact.id.hashCode() and 0x7FFFFFFF) % pastelColors.size
                                        val avatarColor = pastelColors[colorIdx]
                                        val isExpanded = expandedContactId == (-contact.id - 1000L)

                                        OneUiExpandableContactRow(
                                            contact = contact,
                                            avatarColor = avatarColor,
                                            searchQuery = "",
                                            showSubtitleInCollapsed = false,
                                            isExpanded = isExpanded,
                                            onToggleExpand = {
                                                val favKey = -contact.id - 1000L
                                                expandedContactId = if (isExpanded) null else favKey
                                            },
                                            onCallClick = onCallContact,
                                            onMessageClick = handleSwipeMessage,
                                            onVideoClick = handleVideoCall,
                                            onInfoClick = { onContactClick(contact) },
                                        )

                                        if (index < favoriteContacts.lastIndex) {
                                            HorizontalDivider(
                                                color = Color(0xFF2E3138),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: GROUPS PILL (Matches 25619.jpg)
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color(0xFF1E2024),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clickable(onClick = onGroupsClick),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFE2E8F0), CircleShape),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = "Groups",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Groups",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                }

                // SECTION 4: ALPHABETICAL CONTACT CARDS WITH INLINE EXPANSION (Matches 25619.jpg)
                groupedContacts.forEach { (letter, contactsInGroup) ->
                    item(key = "hdr_$letter") {
                        Text(
                            text = letter.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8E8E93),
                            modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
                        )
                    }

                    item(key = "grp_$letter") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E2024),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                contactsInGroup.forEachIndexed { index, contact ->
                                    val colorIdx = (contact.id.hashCode() and 0x7FFFFFFF) % pastelColors.size
                                    val avatarColor = pastelColors[colorIdx]
                                    val isExpanded = expandedContactId == contact.id

                                    OneUiExpandableContactRow(
                                        contact = contact,
                                        avatarColor = avatarColor,
                                        searchQuery = "",
                                        showSubtitleInCollapsed = false,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            expandedContactId = if (isExpanded) null else contact.id
                                        },
                                        onCallClick = onCallContact,
                                        onMessageClick = handleSwipeMessage,
                                        onVideoClick = handleVideoCall,
                                        onInfoClick = { onContactClick(contact) },
                                    )

                                    if (index < contactsInGroup.lastIndex) {
                                        HorizontalDivider(
                                            color = Color(0xFF2E3138),
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // INTERACTIVE EDGE A–Z SCRUBBER
        if (searchQuery.isBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E2024).copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .pointerInput(scrubberLetters, sectionIndexMap) {
                            detectVerticalDragGestures { change, _ ->
                                val totalHeight = size.height.toFloat()
                                if (totalHeight > 0f) {
                                    val touchY = change.position.y
                                    val fraction = (touchY / totalHeight).coerceIn(0f, 0.999f)
                                    val index = (fraction * scrubberLetters.size).toInt()
                                    val letter = scrubberLetters.getOrNull(index) ?: '#'

                                    val targetItemIndex = sectionIndexMap[letter]
                                    if (targetItemIndex != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch {
                                            listState.scrollToItem(targetItemIndex)
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    scrubberLetters.forEach { char ->
                        Text(
                            text = char.toString(),
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneUiExpandableContactRow(
    contact: ContactItem,
    avatarColor: Color,
    searchQuery: String,
    showSubtitleInCollapsed: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onInfoClick: () -> Unit,
) {
    val context = LocalContext.current
    val initial = getContactInitial(contact)
    val effectiveNumber = remember(contact) {
        contact.number.ifBlank {
            contact.primaryNumber.ifBlank {
                contact.phones.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
            }
        }
    }

    OneUiSwipeActionRow(
        phoneNumber = effectiveNumber,
        onSwipeCall = onCallClick,
        onSwipeMessage = onMessageClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E2024))
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
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
                            text = initial,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildHighlightedText(contact.name, searchQuery),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (showSubtitleInCollapsed && effectiveNumber.isNotBlank()) {
                        Text(
                            text = buildHighlightedText(effectiveNumber, searchQuery),
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            // INLINE SAMSUNG ONE UI EXPANSION (Matches 25619.jpg)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                ) {
                    if (effectiveNumber.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .padding(start = 54.dp, bottom = 14.dp)
                                .clickable { onInfoClick() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Mobile ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                            )
                            Text(
                                text = "$effectiveNumber >",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. Green Call Circle
                        Surface(
                            onClick = { onCallClick(effectiveNumber) },
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                        }

                        // 2. Blue SMS Circle
                        Surface(
                            onClick = { onMessageClick(effectiveNumber) },
                            shape = CircleShape,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ChatBubble, contentDescription = "Message", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }

                        // 3. Green Video Call Circle
                        Surface(
                            onClick = { onVideoClick(effectiveNumber) },
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                        }

                        // 4. Contact Info Circle (Opens Full OneUiContactCardScreen)
                        Surface(
                            onClick = onInfoClick,
                            shape = CircleShape,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Info, contentDescription = "Contact Info", tint = Color(0xFF1E2024), modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}