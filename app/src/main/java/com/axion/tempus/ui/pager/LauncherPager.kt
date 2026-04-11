package com.axion.tempus.ui.pager

import android.content.pm.ResolveInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.axion.tempus.ui.notes.NotesScreen
import com.axion.tempus.HomeScreen
import com.axion.tempus.RightPanelScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherPager(apps: List<ResolveInfo>) {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pagerState.currentPage == 0) {
        scope.launch { pagerState.animateScrollToPage(1) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            // Notes page uses edge swipes + full-screen text fields; pager scroll would fight typing.
            userScrollEnabled = pagerState.currentPage != 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> NotesScreen(
                    onNavigateToHome = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> HomeScreen(apps = apps)
                else -> RightPanelScreen()
            }
        }
    }
}
