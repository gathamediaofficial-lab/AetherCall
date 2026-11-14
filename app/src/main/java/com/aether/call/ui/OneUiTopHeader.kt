package com.aether.call.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified Samsung One UI top header with live search expansion and tab-specific actions.
 */
@Composable
fun OneUiTopHeader(
    currentTab: NavigationTab,
    contactsCount: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    isMissedFilterActive: Boolean = false,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAddContactClick: () -> Unit,
    onFilterClick: () -> Unit,
    onOverflowMenuClick: () -> Unit,
    overflowMenuContent: @Composable ColumnScope.() -> Unit = {},
    showOverflowMenu: Boolean = false,
    onDismissOverflowMenu: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        color = Color(0xFF000000),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        if (!isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phone",
                        style = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                    )
                    if (currentTab == NavigationTab.CONTACTS) {
                        Text(
                            text = "$contactsCount contacts with phone numbers",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = Color(0xFF8E8E93),
                            ),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentTab == NavigationTab.CONTACTS) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddContactClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create contact",
                                tint = Color.White,
                            )
                        }
                    }

                    if (currentTab == NavigationTab.RECENTS) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFilterClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter calls",
                                tint = if (isMissedFilterActive) Color(0xFF10B981) else Color.White,
                            )
                        }
                    }

                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSearch()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                        )
                    }

                    Box {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOverflowMenuClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White,
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = onDismissOverflowMenu,
                            containerColor = Color(0xFF1E2024),
                            shape = RoundedCornerShape(20.dp),
                            content = overflowMenuContent,
                        )
                    }
                }
            }
        } else {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSearch()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = Color.White,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E2024),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search contacts, numbers...",
                                            style = TextStyle(color = Color(0xFF8E8E93), fontSize = 15.sp),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF8E8E93),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
