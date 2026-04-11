package com.axion.tempus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.axion.tempus.ui.theme.TempusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fetch the list of installed apps
        val installedApps = getInstalledApps(this)

        setContent {
            TempusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppList(apps = installedApps)
                }
            }
        }
    }

    // Analytical constraint: We only want apps that can be launched.
    private fun getInstalledApps(context: Context): List<ResolveInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Querying activities; flag 0 is sufficient for standard querying
        return packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString() } // Sort alphabetically
    }
}

@Composable
fun AppList(apps: List<ResolveInfo>) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps) { app ->
            val appName = remember { app.loadLabel(packageManager).toString() }
            val packageName = app.activityInfo.packageName
            val componentName = app.activityInfo.name

            AppListItem(
                appName = appName,
                packageName = packageName,
                onClick = {
                    // Execute the launch intent when clicked
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                }
            )
        }
    }
}

@Composable
fun AppListItem(appName: String, packageName: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text = appName, style = MaterialTheme.typography.titleMedium)
        Text(text = packageName, style = MaterialTheme.typography.bodySmall)
    }
}