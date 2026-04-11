package com.axion.tempus.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.axion.tempus.data.LauncherApp
import com.axion.tempus.data.LauncherAppsRepository
import com.axion.tempus.ui.home.LauncherAppIconItem

@Composable
fun RightPanelScreen() {
    val context = LocalContext.current
    val repository = remember(context) { LauncherAppsRepository.get(context) }
    val apps by repository.apps.collectAsStateWithLifecycle()
    val pinnedSlotKeys by repository.pinnedSlotKeys.collectAsStateWithLifecycle()

    var showPinnedSetup by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var activeSlotIndex by remember { mutableStateOf<Int?>(null) }

    val slots = remember(pinnedSlotKeys, apps) {
        repository.resolvedPinnedSlots()
    }

    val filteredApps = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.searchLabel.contains(q) }
    }

    LaunchedEffect(repository) {
        repository.ensureLoaded()
    }

    LaunchedEffect(showPinnedSetup) {
        if (!showPinnedSetup) {
            query = ""
            activeSlotIndex = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        if (showPinnedSetup) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Pinned apps",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Up to four shortcuts on the home screen. Tap a slot, then choose an app — or pick an app to fill the next empty slot.",
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    repeat(4) { index ->
                        PinnedSlotCell(
                            app = slots.getOrNull(index),
                            isActive = activeSlotIndex == index,
                            repository = repository,
                            onSlotClick = { activeSlotIndex = index },
                            onClear = {
                                repository.clearPinnedSlot(index)
                                if (activeSlotIndex == index) activeSlotIndex = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Filter apps",
                            color = Color(0xFF666666)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF141414),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedPlaceholderColor = Color(0xFF666666),
                        unfocusedPlaceholderColor = Color(0xFF666666)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                        AppPickerRow(
                            app = app,
                            onClick = {
                                if (activeSlotIndex != null) {
                                    repository.setPinnedSlot(activeSlotIndex!!, app)
                                    activeSlotIndex = null
                                } else {
                                    val emptyIndex = pinnedSlotKeys.indexOfFirst { it == null }
                                    if (emptyIndex >= 0) {
                                        repository.setPinnedSlot(emptyIndex, app)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(
                onClick = { showPinnedSetup = !showPinnedSetup }
            ) {
                Text(
                    text = if (showPinnedSetup) "Done" else "Set up pinned apps",
                    color = Color(0xFFCCCCCC)
                )
            }
            IconButton(
                onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Open settings",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PinnedSlotCell(
    app: LauncherApp?,
    isActive: Boolean,
    repository: LauncherAppsRepository,
    onSlotClick: () -> Unit,
    onClear: () -> Unit
) {
    val borderColor = when {
        isActive -> Color(0xFFCCCCCC)
        else -> Color(0xFF444444)
    }

    Box(
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        if (app == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .size(width = 84.dp, height = 108.dp)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onSlotClick)
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add pin",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Empty",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        } else {
            Box {
                LauncherAppIconItem(
                    app = app,
                    repository = repository,
                    onClick = onSlotClick
                )
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove pin",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: LauncherApp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = app.label,
            color = Color(0xFFCCCCCC),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
