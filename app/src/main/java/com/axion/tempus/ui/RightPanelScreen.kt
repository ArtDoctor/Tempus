package com.axion.tempus.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.axion.tempus.findActivity
import com.axion.tempus.data.LauncherApp
import com.axion.tempus.data.LauncherAppsRepository
import com.axion.tempus.ui.home.LauncherAppIconItem

private val PinnedSetupEnterMs = 240
private val PinnedSetupExitMs = 180
private val SlotCrossfadeMs = 200
private val ListPlacementMs = 280

@Composable
fun RightPanelScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { LauncherAppsRepository.get(context) }
    val apps by repository.apps.collectAsStateWithLifecycle()
    val pinnedSlotKeys by repository.pinnedSlotKeys.collectAsStateWithLifecycle()

    var showPinnedSetup by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var activeSlotIndex by remember { mutableStateOf<Int?>(null) }
    var launcherStatusRefreshTick by rememberSaveable { mutableStateOf(0) }
    val isDefaultLauncher = remember(context, launcherStatusRefreshTick) {
        context.isDefaultLauncherApp()
    }
    val launcherRoleRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        launcherStatusRefreshTick++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                launcherStatusRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val slots = remember(pinnedSlotKeys, apps) {
        repository.resolvedPinnedSlots()
    }

    var filteredApps by remember { mutableStateOf(emptyList<LauncherApp>()) }
    LaunchedEffect(query, apps) {
        val q = query.trim().lowercase()
        val appList = apps
        val result = withContext(Dispatchers.Default) {
            if (q.isEmpty()) appList
            else appList.filter { it.searchLabel.contains(q) }
        }
        if (q == query.trim().lowercase()) {
            filteredApps = result
        }
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
        Button(
            onClick = {
                context.launchDefaultLauncherSetup(
                    onRequestRole = { intent -> launcherRoleRequest.launch(intent) }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3A3A3A),
                contentColor = Color(0xFFE8E8E8),
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor = Color(0xFF888888)
            )
        ) {
            Text(
                text = if (isDefaultLauncher) {
                    "Manage default launcher"
                } else {
                    "Make app default launcher"
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isDefaultLauncher) {
                "Tempus is currently your default Home app."
            } else {
                "Set Tempus as your Home app so it opens when you press Home."
            },
            color = Color(0xFF888888),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showPinnedSetup,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(animationSpec = tween(PinnedSetupEnterMs)) +
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(PinnedSetupEnterMs, easing = FastOutSlowInEasing)
                    ),
                exit = fadeOut(animationSpec = tween(PinnedSetupExitMs)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(PinnedSetupExitMs, easing = FastOutSlowInEasing)
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
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
                        items(
                            items = filteredApps,
                            key = { "${it.packageName}/${it.activityName}" }
                        ) { app ->
                            AppPickerRow(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = tween(
                                        durationMillis = ListPlacementMs,
                                        easing = FastOutSlowInEasing
                                    )
                                ),
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
            }
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
                    val settingsIntent = Intent(AndroidSettings.ACTION_SETTINGS).apply {
                        if (context.findActivity() == null) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(settingsIntent)
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

private fun android.content.Context.isDefaultLauncherApp(): Boolean {
    val resolvedLauncher = packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        0
    ) ?: return false
    return resolvedLauncher.activityInfo?.packageName == packageName
}

private fun android.content.Context.launchDefaultLauncherSetup(
    onRequestRole: (Intent) -> Unit
) {
    val activity = findActivity()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        if (
            roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME) &&
            activity != null
        ) {
            onRequestRole(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            return
        }
    }

    val settingsIntent = Intent(AndroidSettings.ACTION_HOME_SETTINGS).apply {
        if (activity == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    startActivity(settingsIntent)
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
        Crossfade(
            targetState = app,
            animationSpec = tween(SlotCrossfadeMs, easing = FastOutSlowInEasing),
            label = "pinnedSlot"
        ) { slotApp ->
            when (slotApp) {
                null -> Column(
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
                else -> Box {
                    LauncherAppIconItem(
                        app = slotApp,
                        repository = repository,
                        onClick = { onSlotClick() }
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
}

@Composable
private fun AppPickerRow(
    modifier: Modifier = Modifier,
    app: LauncherApp,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
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
