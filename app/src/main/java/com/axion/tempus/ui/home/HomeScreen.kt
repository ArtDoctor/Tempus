package com.axion.tempus.ui.home

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.axion.tempus.findActivity
import com.axion.tempus.data.LauncherApp
import com.axion.tempus.data.LauncherAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.axion.tempus.NotificationShadeAccessibilityService
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { LauncherAppsRepository.get(context) }
    val apps by repository.apps.collectAsStateWithLifecycle()
    val pinnedSlotKeys by repository.pinnedSlotKeys.collectAsStateWithLifecycle()
    val searchLaunchCounts by repository.searchLaunchCounts.collectAsStateWithLifecycle()

    var searchFieldFocused by remember { mutableStateOf(false) }
    var keyboardWasVisibleForSearch by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val needsClearSearchOnResume = remember { mutableStateOf(false) }
    val clearSearchAndFocus = rememberUpdatedState {
        onSearchQueryChange("")
        focusManager.clearFocus()
    }
    val timerMinutes = remember(searchQuery) { parseTimerMinutes(searchQuery) }
    val openNotificationShade = rememberUpdatedState {
        focusManager.clearFocus()
        context.openNotificationShadeOrPrompt()
    }
    val launchWebSearchAndReset = rememberUpdatedState {
        needsClearSearchOnResume.value = true
        clearSearchAndFocus.value()
        launchWebSearch(context, searchQuery)
    }
    val launchClockTimerAndReset = rememberUpdatedState {
        val minutes = parseTimerMinutes(searchQuery) ?: return@rememberUpdatedState
        needsClearSearchOnResume.value = true
        clearSearchAndFocus.value()
        launchClockTimer(context, minutes)
    }

    BackHandler(enabled = searchFieldFocused) {
        clearSearchAndFocus.value()
    }

    LaunchedEffect(searchFieldFocused, isImeVisible) {
        when {
            searchFieldFocused && isImeVisible -> {
                keyboardWasVisibleForSearch = true
            }

            searchFieldFocused && keyboardWasVisibleForSearch && !isImeVisible -> {
                keyboardWasVisibleForSearch = false
                clearSearchAndFocus.value()
            }

            !searchFieldFocused -> {
                keyboardWasVisibleForSearch = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && needsClearSearchOnResume.value) {
                clearSearchAndFocus.value()
                needsClearSearchOnResume.value = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pinnedApps = remember(pinnedSlotKeys, apps) {
        repository.resolvedPinnedApps()
    }

    LaunchedEffect(repository) {
        repository.ensureLoaded()
    }

    var topMatches by remember { mutableStateOf(emptyList<LauncherApp>()) }
    LaunchedEffect(searchQuery, apps, searchLaunchCounts) {
        val q = searchQuery
        val appList = apps
        val counts = searchLaunchCounts
        val result = withContext(Dispatchers.Default) {
            topMatchingApps(q, appList, counts)
        }
        if (q == searchQuery) {
            topMatches = result
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(searchFieldFocused) {
                detectNotificationShadeSwipe(
                    enabled = !searchFieldFocused,
                    onSwipeDown = openNotificationShade.value
                )
            }
            .pointerInput(focusManager) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        HomeClock(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 32.dp)
                .padding(horizontal = 24.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                AnimatedVisibility(
                    visible = pinnedApps.isNotEmpty() && !searchFieldFocused,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(90))
                ) {
                    Column {
                        LauncherAppIconRow(
                            apps = pinnedApps,
                            repository = repository,
                            onAppClick = { app, sourceBounds ->
                                repository.recordSearchLaunch(app)
                                launchApp(context, app, sourceBounds)
                            },
                            onAppLongClick = { app ->
                                launchAppSettingsScreen(context, app)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                AnimatedVisibility(
                    visible = searchFieldFocused && searchQuery.isNotBlank(),
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(90))
                ) {
                    Column {
                        if (timerMinutes != null) {
                            TimerActionRow(
                                minutes = timerMinutes,
                                onClick = { launchClockTimerAndReset.value() }
                            )
                        }
                        SearchActionRow(
                            query = searchQuery,
                            onClick = { launchWebSearchAndReset.value() }
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            if (native.keyCode != AndroidKeyEvent.KEYCODE_BACK ||
                                native.action != AndroidKeyEvent.ACTION_DOWN
                            ) {
                                return@onPreviewKeyEvent false
                            }
                            if (!searchFieldFocused && searchQuery.isEmpty()) {
                                return@onPreviewKeyEvent false
                            }
                            clearSearchAndFocus.value()
                            true
                        }
                        .fillMaxWidth()
                        .onFocusChanged { searchFieldFocused = it.isFocused },
                    placeholder = {
                        Text(
                            text = "Search apps",
                            color = Color(0xFF666666)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFFAAAAAA)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { launchWebSearchAndReset.value() }
                    ),
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

                AnimatedVisibility(
                    visible = searchFieldFocused && searchQuery.isNotBlank() && topMatches.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(90))
                ) {
                    LauncherAppIconRow(
                        apps = topMatches,
                        repository = repository,
                        onAppClick = { app, sourceBounds ->
                            repository.recordSearchLaunch(app)
                            needsClearSearchOnResume.value = true
                            clearSearchAndFocus.value()
                            launchApp(context, app, sourceBounds)
                        },
                        onAppLongClick = { app ->
                            needsClearSearchOnResume.value = true
                            clearSearchAndFocus.value()
                            launchAppSettingsScreen(context, app)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerActionRow(
    minutes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Timer,
            contentDescription = "Start timer",
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = "Start $minutes min timer",
            color = Color(0xFFCCCCCC),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
private fun SearchActionRow(
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search the web",
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = query,
            color = Color(0xFFCCCCCC),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
private fun HomeClock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf(formatTime()) }
    var dateText by remember { mutableStateOf(formatDate()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = LocalContext.current.applicationContext
    val context = LocalContext.current

    val applyClockNow = remember {
        {
            timeText = formatTime()
            dateText = formatDate()
        }
    }

    DisposableEffect(lifecycleOwner, appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                applyClockNow()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        var registered = false
        fun register() {
            if (registered) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
        }
        fun unregister() {
            if (!registered) return
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    applyClockNow()
                    register()
                }
                Lifecycle.Event.ON_STOP -> unregister()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            applyClockNow()
            register()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    Column(
        modifier = modifier
            .clickable(
                onClickLabel = "Open Clock",
                role = Role.Button,
                onClick = { launchDefaultClockApp(context) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 64.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                lineHeight = 72.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dateText,
            color = Color(0xFFB0B0B0),
            fontSize = 18.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun launchApp(context: Context, app: LauncherApp, sourceBounds: Rect? = null) {
    val componentName = ComponentName(app.packageName, app.activityName)
    val launcherApps = context.getSystemService(LauncherApps::class.java)

    val launchedWithLauncherApps = runCatching {
        launcherApps?.startMainActivity(
            componentName,
            Process.myUserHandle(),
            sourceBounds,
            null
        )
        launcherApps != null
    }.getOrDefault(false)

    if (launchedWithLauncherApps) {
        return
    }

    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = componentName
        if (context.findActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    runCatching {
        context.startActivity(intent)
    }.getOrElse {
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { fallback ->
            if (context.findActivity() == null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}

private fun launchDefaultClockApp(context: Context) {
    val newTaskFlag = if (context.findActivity() == null) Intent.FLAG_ACTIVITY_NEW_TASK else 0
    val pm = context.packageManager

    fun Intent.withNewTask(): Intent = apply { addFlags(newTaskFlag) }

    fun tryStart(i: Intent): Boolean =
        runCatching {
            context.startActivity(i.withNewTask())
            true
        }.getOrDefault(false)

    if (tryStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))) return

    val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
    @Suppress("DEPRECATION")
    val alarmHandlers = pm.queryIntentActivities(showAlarms, PackageManager.MATCH_DEFAULT_ONLY)
    val firstAlarm = alarmHandlers.firstOrNull()?.activityInfo
    if (firstAlarm != null) {
        val explicit = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            setClassName(firstAlarm.packageName, firstAlarm.name)
        }
        if (tryStart(explicit)) return
    }

    if (tryStart(Intent(AlarmClock.ACTION_SHOW_TIMERS))) return

    if (Build.VERSION.SDK_INT >= 35) {
        val appClock = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK")
        if (appClock.resolveActivity(pm) != null && tryStart(appClock)) return
    }

    for (pkg in CLOCK_LAUNCH_PACKAGES) {
        val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
        if (tryStart(launch)) return
    }

    Toast.makeText(
        context,
        "Couldn't open Clock.",
        Toast.LENGTH_SHORT
    ).show()
}

/** Packages queried in the manifest; order is generic → OEM-specific. */
private val CLOCK_LAUNCH_PACKAGES = listOf(
    "com.google.android.deskclock",
    "com.android.deskclock",
    "com.sec.android.app.clockpackage",
    "com.samsung.android.app.clockpackage",
    "com.oneplus.deskclock",
    "com.coloros.alarmclock",
    "com.oplus.alarmclock",
    "com.miui.clock",
)

private const val MAX_TIMER_MINUTES = Int.MAX_VALUE / 60

private fun launchClockTimer(context: Context, minutes: Int) {
    val newTaskFlag = if (context.findActivity() == null) Intent.FLAG_ACTIVITY_NEW_TASK else 0
    val seconds = minutes * 60
    val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        putExtra(AlarmClock.EXTRA_MESSAGE, "Tempus timer")
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        addFlags(newTaskFlag)
    }

    runCatching {
        context.startActivity(timerIntent)
    }.getOrElse {
        Toast.makeText(
            context,
            "Couldn't start timer.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun launchWebSearch(context: Context, query: String) {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return

    val newTaskFlag = if (context.findActivity() == null) Intent.FLAG_ACTIVITY_NEW_TASK else 0

    val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, trimmedQuery)
        addFlags(newTaskFlag)
    }

    val fallbackIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/search?q=${Uri.encode(trimmedQuery)}")
    ).apply {
        addFlags(newTaskFlag)
    }

    runCatching {
        context.startActivity(webSearchIntent)
    }.getOrElse {
        runCatching {
            context.startActivity(fallbackIntent)
        }
    }
}

private fun parseTimerMinutes(query: String): Int? {
    val minutes = query.trim().toIntOrNull() ?: return null
    return minutes.takeIf { it in 1..MAX_TIMER_MINUTES }
}

private fun launchAppSettingsScreen(context: Context, app: LauncherApp) {
    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${app.packageName}")
        if (context.findActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    runCatching {
        context.startActivity(settingsIntent)
    }.getOrElse {
        Toast.makeText(
            context,
            "Couldn't open app settings.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun formatTime(): String {
    return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
}

private fun formatDate(): String {
    return LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}

private fun matchScore(query: String, app: LauncherApp): Int {
    val trimmedQuery = query.trim().lowercase()
    if (trimmedQuery.isEmpty()) return Int.MAX_VALUE

    return when {
        app.searchLabel.startsWith(trimmedQuery) -> {
            trimmedQuery.length * -100 - (1000 - minOf(app.searchLabel.length, 100))
        }

        app.searchLabel.contains(trimmedQuery) -> {
            10_000 + app.searchLabel.indexOf(trimmedQuery) * 10 + app.searchLabel.length
        }

        else -> Int.MAX_VALUE
    }
}

private fun topMatchingApps(
    query: String,
    apps: List<LauncherApp>,
    searchLaunchCounts: Map<String, Int>
): List<LauncherApp> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return emptyList()

    return apps.asSequence()
        .mapNotNull { app ->
            val score = matchScore(trimmedQuery, app)
            if (score == Int.MAX_VALUE) null else app to score
        }
        .sortedWith(
            compareBy<Pair<LauncherApp, Int>> { it.second }
                .thenByDescending { (app, _) -> searchLaunchCounts[searchLaunchKey(app)] ?: 0 }
                .thenBy { it.first.searchLabel }
        )
        .take(4)
        .map { it.first }
        .toList()
}

private fun searchLaunchKey(app: LauncherApp): String {
    return "${app.packageName}/${app.activityName}"
}

private suspend fun PointerInputScope.detectNotificationShadeSwipe(
    enabled: Boolean,
    onSwipeDown: () -> Unit
) {
    if (!enabled) return

    val triggerDistance = viewConfiguration.touchSlop * 2f

    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val pointerId = down.id
        var totalDx = 0f
        var totalDy = 0f
        var triggered = false

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

            totalDx += change.positionChange().x
            totalDy += change.positionChange().y

            if (!triggered &&
                totalDy > triggerDistance &&
                totalDy > abs(totalDx) * 1.5f
            ) {
                triggered = true
                change.consume()
                onSwipeDown()
            }

            if (!change.pressed) {
                break
            }

            if (triggered) {
                event.changes.forEach { pointerChange ->
                    if (pointerChange.pressed) {
                        pointerChange.consume()
                    }
                }
            }
        }
    }
}

private fun Context.openNotificationShadeOrPrompt() {
    NotificationShadeAccessibilityService.expandNotificationsPanel()
}
