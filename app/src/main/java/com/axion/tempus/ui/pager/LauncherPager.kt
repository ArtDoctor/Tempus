package com.axion.tempus.ui.pager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.axion.tempus.ui.RightPanelScreen
import com.axion.tempus.ui.home.HomeScreen
import com.axion.tempus.ui.notes.NotesScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherPager(homeIntentVersion: Int = 0) {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()

    fun navigateHome() {
        scope.launch { pagerState.animateScrollToPage(1) }
    }

    BackHandler(enabled = pagerState.currentPage != 1) {
        navigateHome()
    }

    LaunchedEffect(homeIntentVersion) {
        if (homeIntentVersion > 0 && pagerState.currentPage != 1) {
            pagerState.animateScrollToPage(1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                )
            )
    ) {
        HorizontalPager(
            state = pagerState,
            // Only compose the visible page so off-screen tabs (notes, settings) do not run timers, VM, or collectors.
            beyondViewportPageCount = 0,
            // Notes page uses edge swipes + full-screen text fields; pager scroll would fight typing.
            userScrollEnabled = pagerState.currentPage != 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> NotesScreen(
                    onNavigateToHome = ::navigateHome
                )
                1 -> HomeScreen()
                else -> RightPanelScreen()
            }
        }
    }
}
