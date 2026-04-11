package com.axion.tempus.ui.pager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
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
    val focusManager = LocalFocusManager.current
    var homeSearchQuery by remember { mutableStateOf("") }
    val navigateHome = remember(pagerState, scope) {
        {
            scope.launch { pagerState.animateScrollToPage(1) }
            Unit
        }
    }

    // Always handle back here: on home, consume the press so the activity default (and any
    // HOME re-delivery / task transition) does not run and replay a pointless animation.
    BackHandler {
        if (pagerState.currentPage != 1) {
            navigateHome()
        }
    }

    LaunchedEffect(homeIntentVersion) {
        if (homeIntentVersion > 0 && pagerState.currentPage != 1) {
            pagerState.scrollToPage(1)
        }
    }

    // Dismiss IME as soon as the user starts swiping away from home (target page changes).
    LaunchedEffect(pagerState.targetPage) {
        if (pagerState.targetPage != 1) {
            focusManager.clearFocus()
        }
    }

    // Clear search when leaving home so the field is empty when swiping back; also clears if
    // the pager settles on another page while the keyboard was still up.
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != 1) {
            homeSearchQuery = ""
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                    onNavigateToHome = navigateHome
                )
                1 -> HomeScreen(
                    searchQuery = homeSearchQuery,
                    onSearchQueryChange = { homeSearchQuery = it }
                )
                else -> RightPanelScreen()
            }
        }
    }
}
