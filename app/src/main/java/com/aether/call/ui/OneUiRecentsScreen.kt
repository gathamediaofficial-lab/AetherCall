package com.aether.call.ui

import android.provider.CallLog
import com.aether.call.data.CallLogItem
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.call.util.PhoneNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CallFilterMode(val title: String, val callLogType: Int?) {
    ALL("All calls", null),
    MISSED("Missed calls", CallLog.Calls.MISSED_TYPE),
    REJECTED("Rejected calls", CallLog.Calls.REJECTED_TYPE),
    OUTGOING("Outgoing calls", CallLog.Calls.OUTGOING_TYPE),
    INCOMING("Incoming calls", CallLog.Calls.INCOMING_TYPE),
}

private data class GroupedCallLogRow(
    val representative: CallLogItem,
    val count: Int,
)

@Composable
fun OneUiRecentsScreen(
    callLogs: List<CallLogItem>,
    isMissedFilterActive: Boolean = false,
    onClearMissedFilter: () -> Unit = {},
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onVideoCallClick: (String) -> Unit = {},
    onCallDetailsClick: (CallLogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(CallFilterMode.ALL) }
    var showFilterModal by rememberSaveable { mutableStateOf(false) }
    var pendingFilterSelection by rememberSaveable { mutableStateOf(CallFilterMode.ALL) }
    var expandedLogId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Trigger filter modal when the top-bar filter button is tapped
    LaunchedEffect(isMissedFilterActive) {
        if (isMissedFilterActive) {
            pendingFilterSelection = selectedFilter
            showFilterModal = true
            onClearMissedFilter()
        }
    }

    val filteredLogs = remember(callLogs, selectedFilter) {
        val targetType = selectedFilter.callLogType
        if (targetType == null) {
            callLogs
        } else {
            callLogs.filter { it.type == targetType }
        }
    }

    // Group logs by Date Header ("Today", "Yesterday", "12 Nov 2026") and collapse consecutive identical calls
    val dateGroupedRows = remember(filteredLogs) {
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val byDate = filteredLogs.groupBy { log ->
            val isToday = DateUtils.isToday(log.dateMillis)
            val cal = Calendar.getInstance().apply { timeInMillis = log.dateMillis }
            val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
            val isYesterday = cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

            when {
                isToday -> "Today"
                isYesterday -> "Yesterday"
                else -> dateFormat.format(Date(log.dateMillis))
            }
        }

        byDate.mapValues { (_, logsInDay) ->
            val collapsed = mutableListOf<GroupedCallLogRow>()
            for (log in logsInDay) {
                val prev = collapsed.lastOrNull()
                val prevNorm = prev?.let { PhoneNormalizer.normalizeDigits(it.representative.number) }.orEmpty()
                val currNorm = PhoneNormalizer.normalizeDigits(log.number)

                if (prev != null && prevNorm.isNotEmpty() && prevNorm == currNorm && prev.representative.type == log.type) {
                    collapsed[collapsed.lastIndex] = prev.copy(count = prev.count + 1)
                } else {
                    collapsed.add(GroupedCallLogRow(representative = log, count = 1))
                }
            }
            collapsed
        }
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Active Filter Chip Bar (if filtered to Missed / Rejected / Outgoing / Incoming)
            if (selectedFilter != CallFilterMode.ALL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        onClick = {
                            pendingFilterSelection = selectedFilter
                            showFilterModal = true
                        },
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedFilter.title,
                                color = Color(0xFF10B981),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    TextButton(onClick = { selectedFilter = CallFilterMode.ALL }) {
                        Text("Show all", color = Color(0xFF38BDF8), fontSize = 13.sp)
                    }
                }
            }

            if (dateGroupedRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No ${selectedFilter.title.lowercase()}",
                        color = Color(0xFF8E8E93),
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    dateGroupedRows.forEach { (dateLabel, rows) ->
                        item(key = "header_$dateLabel") {
                            Text(
                                text = dateLabel,
                                color = Color(0xFF9E9E9E),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
                            )
                        }

                        item(key = "card_$dateLabel") {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF1E2024),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    rows.forEachIndexed { idx, groupedRow ->
                                        val log = groupedRow.representative
                                        val isExpanded = expandedLogId == log.id

                                        OneUiSwipeActionRow(
                                            phoneNumber = log.number,
                                            onSwipeCall = onCallClick,
                                            onSwipeMessage = onMessageClick,
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF1E2024))
                                                    .clickable {
                                                        expandedLogId = if (isExpanded) null else log.id
                                                    }
                                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    val (icon, tint) = when (log.type) {
                                                        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF10B981)
                                                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF94A3B8)
                                                        CallLog.Calls.REJECTED_TYPE -> Icons.Default.Block to Color(0xFFEF4444)
                                                        else -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFEF4444)
                                                    }

                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = tint,
                                                        modifier = Modifier.size(17.dp),
                                                    )

                                                    Spacer(modifier = Modifier.width(14.dp))

                                                    val baseTitle = log.name?.takeIf { it.isNotBlank() } ?: log.number
                                                    val displayTitle = if (groupedRow.count > 1) {
                                                        "$baseTitle (${groupedRow.count})"
                                                    } else {
                                                        baseTitle
                                                    }

                                                    val isMissed = log.type == CallLog.Calls.MISSED_TYPE

                                                    Text(
                                                        text = displayTitle,
                                                        fontSize = 16.sp,
                                                        fontWeight = if (isMissed) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isMissed) Color(0xFFEF4444) else Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )

                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Icon(
                                                            imageVector = Icons.Default.Wifi,
                                                            contentDescription = null,
                                                            tint = Color(0xFF8E8E93),
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = timeFormatter.format(Date(log.dateMillis)),
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF8E8E93),
                                                        )
                                                    }
                                                }

                                                // Inline Samsung One UI Expandable Action Row
                                                AnimatedVisibility(
                                                    visible = isExpanded,
                                                    enter = expandVertically() + fadeIn(),
                                                    exit = shrinkVertically() + fadeOut(),
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 12.dp),
                                                    ) {
                                                        Text(
                                                            text = "Mobile ${log.number} • ${log.simDisplayName}",
                                                            color = Color(0xFF10B981),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(start = 31.dp, bottom = 12.dp),
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                                        ) {
                                                            RecentsActionCircle(
                                                                icon = Icons.Default.Call,
                                                                bgColor = Color(0xFF10B981),
                                                                onClick = { onCallClick(log.number) },
                                                            )
                                                            RecentsActionCircle(
                                                                icon = Icons.Default.ChatBubble,
                                                                bgColor = Color(0xFF38BDF8),
                                                                onClick = { onMessageClick(log.number) },
                                                            )
                                                            RecentsActionCircle(
                                                                icon = Icons.Default.Videocam,
                                                                bgColor = Color(0xFF10B981),
                                                                onClick = {
                                                                    onVideoCallClick(log.number)
                                                                },
                                                            )
                                                            RecentsActionCircle(
                                                                icon = Icons.Default.Info,
                                                                bgColor = Color(0xFF3A3D45),
                                                                onClick = { onCallDetailsClick(log) },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (idx < rows.lastIndex) {
                                            HorizontalDivider(
                                                color = Color(0xFF2E3138),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(start = 48.dp, end = 18.dp),
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
    }

    // Authentic One UI 5-Option "Filter calls" Radio Dialog (matches 25617.jpg)
    if (showFilterModal) {
        AlertDialog(
            onDismissRequest = { showFilterModal = false },
            containerColor = Color(0xFF25272C),
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = "Filter calls",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CallFilterMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { pendingFilterSelection = mode }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = pendingFilterSelection == mode,
                                onClick = { pendingFilterSelection = mode },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF10B981),
                                    unselectedColor = Color(0xFF8E8E93),
                                ),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = mode.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showFilterModal = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color(0xFF3E4149)),
                    )
                    TextButton(
                        onClick = {
                            selectedFilter = pendingFilterSelection
                            showFilterModal = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("OK", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
        )
    }
}

@Composable
private fun RecentsActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}