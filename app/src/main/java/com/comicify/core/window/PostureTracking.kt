package com.comicify.core.window

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

@Composable
fun rememberReadingPosture(): ReadingPosture {
    val activity = LocalContext.current as Activity
    val configuration = LocalConfiguration.current

    val fold by remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
            }
    }.collectAsStateWithLifecycle(initialValue = null)

    val windowState = WindowState(
        widthDp = configuration.screenWidthDp,
        isLandscape = configuration.screenWidthDp > configuration.screenHeightDp,
        isHalfOpen = fold?.state == FoldingFeature.State.HALF_OPENED,
        hingeIsHorizontal = fold?.orientation == FoldingFeature.Orientation.HORIZONTAL,
    )

    return windowState.toPosture()
}
