package com.axion.tempus.ui.home

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axion.tempus.data.LauncherApp
import com.axion.tempus.data.LauncherAppsRepository

private val ItemWidth = 84.dp
private val ItemSpacing = 8.dp

@Composable
fun LauncherAppIconRow(
    apps: List<LauncherApp>,
    repository: LauncherAppsRepository,
    onAppClick: (LauncherApp, Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val leftAlignedWidth = ItemWidth * apps.size + ItemSpacing * (apps.size - 1).coerceAtLeast(0)

    Row(
        modifier = modifier
            .then(
                if (apps.size == 4) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.widthIn(max = leftAlignedWidth)
                }
            )
            .heightIn(min = 108.dp)
            .padding(top = 12.dp),
        horizontalArrangement = if (apps.size == 4) {
            Arrangement.SpaceEvenly
        } else {
            Arrangement.spacedBy(ItemSpacing, Alignment.Start)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            LauncherAppIconItem(
                app = app,
                repository = repository,
                onClick = { sourceBounds -> onAppClick(app, sourceBounds) }
            )
        }
    }
}

@Composable
fun LauncherAppIconItem(
    app: LauncherApp,
    repository: LauncherAppsRepository,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val initialIcon = remember(repository, app, iconPx) {
        repository.peekIcon(app, iconPx)?.asImageBitmap()
    }
    val icon by produceState<ImageBitmap?>(
        initialValue = initialIcon,
        key1 = repository,
        key2 = "${app.packageName}/${app.activityName}@$iconPx"
    ) {
        value = repository.getIcon(app, iconPx)?.asImageBitmap()
    }
    var sourceBounds by remember(app.packageName, app.activityName) { mutableStateOf<Rect?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(ItemWidth)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                sourceBounds = Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            }
            .clickable { onClick(sourceBounds) }
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon!!,
                contentDescription = app.label,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Spacer(modifier = Modifier.size(56.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
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
