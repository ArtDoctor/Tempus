package com.axion.tempus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.axion.tempus.ui.pager.LauncherPager
import com.axion.tempus.ui.theme.TempusTheme

class MainActivity : ComponentActivity() {
    private var homeIntentVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            TempusTheme {
                LauncherPager(homeIntentVersion = homeIntentVersion)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isHomeIntent()) {
            homeIntentVersion++
        }
    }
}

private fun Intent.isHomeIntent(): Boolean {
    val categories = categories ?: emptySet()
    return action == Intent.ACTION_MAIN &&
        (Intent.CATEGORY_HOME in categories || Intent.CATEGORY_LAUNCHER in categories)
}
