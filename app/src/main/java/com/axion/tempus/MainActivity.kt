package com.axion.tempus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.axion.tempus.ui.pager.LauncherPager
import com.axion.tempus.ui.theme.TempusTheme
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val installedApps = getInstalledApps(this)

        setContent {
            TempusTheme {
                LauncherPager(apps = installedApps)
            }
        }
    }

    private fun getInstalledApps(context: Context): List<ResolveInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString() }
    }
}

@Composable
fun RightPanelScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        IconButton(
            onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Open settings",
                tint = Color.White
            )
        }
    }
}

@Composable
fun HomeScreen(apps: List<ResolveInfo>) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf(formatTime()) }
    var dateText by remember { mutableStateOf(formatDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timeText = formatTime()
            dateText = formatDate()
        }
    }

    val topMatches = remember(query, apps) {
        topMatchingApps(query, apps, packageManager)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 32.dp)
                .padding(horizontal = 24.dp),
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            AnimatedVisibility(
                visible = query.isNotBlank() && topMatches.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(120))
            ) {
                val resultsBarModifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 108.dp)
                    .padding(bottom = 12.dp)
                if (topMatches.size == 4) {
                    Row(
                        modifier = resultsBarModifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        topMatches.forEach { app ->
                            key(app.activityInfo.packageName) {
                                val packageName = app.activityInfo.packageName
                                val appName = remember {
                                    app.loadLabel(packageManager).toString()
                                }
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SearchAppIconItem(
                                        resolveInfo = app,
                                        appName = appName,
                                        packageManager = packageManager,
                                        onClick = {
                                            val launchIntent =
                                                packageManager.getLaunchIntentForPackage(
                                                    packageName
                                                )
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        modifier = resultsBarModifier,
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(
                            items = topMatches,
                            key = { it.activityInfo.packageName }
                        ) { app ->
                            val packageName = app.activityInfo.packageName
                            val appName = remember(packageName) {
                                app.loadLabel(packageManager).toString()
                            }
                            SearchAppIconItem(
                                resolveInfo = app,
                                appName = appName,
                                packageManager = packageManager,
                                onClick = {
                                    val launchIntent =
                                        packageManager.getLaunchIntentForPackage(packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search apps",
                        color = Color(0xFF666666)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
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
        }
    }
}

@Composable
private fun SearchAppIconItem(
    resolveInfo: ResolveInfo,
    appName: String,
    packageManager: PackageManager,
    onClick: () -> Unit
) {
    val iconPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val bitmap = remember(resolveInfo.activityInfo.packageName) {
        resolveInfo.loadIcon(packageManager).toBitmap(iconPx, iconPx)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = appName,
            modifier = Modifier.size(56.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = appName,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatTime(): String {
    val t = LocalTime.now()
    return String.format("%02d:%02d", t.hour, t.minute)
}

private fun formatDate(): String {
    val d = LocalDate.now()
    val month = d.month.name.take(3)
    return "${d.dayOfMonth} $month"
}

private fun matchScore(query: String, appName: String): Int {
    val q = query.trim().lowercase()
    val n = appName.lowercase()
    if (q.isEmpty()) return Int.MAX_VALUE
    return when {
        n.startsWith(q) -> q.length * -100 - (1000 - minOf(n.length, 100))
        n.contains(q) -> 10_000 + n.indexOf(q) * 10 + n.length
        else -> Int.MAX_VALUE
    }
}

private fun topMatchingApps(
    query: String,
    apps: List<ResolveInfo>,
    pm: PackageManager
): List<ResolveInfo> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return apps
        .mapNotNull { app ->
            val name = app.loadLabel(pm).toString()
            val score = matchScore(q, name)
            if (score == Int.MAX_VALUE) null else app to score
        }
        .sortedWith(compareBy<Pair<ResolveInfo, Int>> { it.second }.thenBy { it.first.loadLabel(pm).toString() })
        .take(4)
        .map { it.first }
}
